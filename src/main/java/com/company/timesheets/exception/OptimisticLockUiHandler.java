package com.company.timesheets.exception;

import io.jmix.core.Messages;
import io.jmix.core.MessageTools;
import io.jmix.core.Metadata;
import io.jmix.core.MetadataTools;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.core.metamodel.model.MetaProperty;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.exception.AbstractUiExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells the user who changed the object, and when, if a save fails because someone saved it first.
 * Runs before the standard Jmix handler ({@code @Order(150)}) and leaves the exception to it when the object
 * has no audit attributes or is gone.
 */
@Component("ts_OptimisticLockUiHandler")
@Order(100)
public class OptimisticLockUiHandler extends AbstractUiExceptionHandler {

    private static final String ECLIPSELINK_EXCEPTION = "org.eclipse.persistence.exceptions.OptimisticLockException";
    private static final String MODIFIED_BY = "lastModifiedBy";
    private static final String MODIFIED_DATE = "lastModifiedDate";
    /**
     * A merge conflict carries no object, only its text: "The object [com.company.Entity-uuid [detached]] ...".
     */
    private static final Pattern STALE_OBJECT = Pattern.compile("\\[([\\w.$]+)-([0-9a-fA-F-]{36})[\\s\\]]");

    private final UnconstrainedDataManager dataManager;
    private final Metadata metadata;
    private final MetadataTools metadataTools;
    private final MessageTools messageTools;
    private final Messages messages;
    private final Notifications notifications;

    public OptimisticLockUiHandler(UnconstrainedDataManager dataManager, Metadata metadata,
                                   MetadataTools metadataTools, MessageTools messageTools, Messages messages,
                                   Notifications notifications) {
        super(ECLIPSELINK_EXCEPTION, jakarta.persistence.OptimisticLockException.class.getName());
        this.dataManager = dataManager;
        this.metadata = metadata;
        this.metadataTools = metadataTools;
        this.messageTools = messageTools;
        this.messages = messages;
        this.notifications = notifications;
    }

    @Override
    protected boolean canHandle(String className, String message, @Nullable Throwable throwable) {
        return describe(throwable).isPresent();
    }

    @Override
    protected void doHandle(String className, String message, @Nullable Throwable throwable) {
        describe(throwable).ifPresent(text -> notifications.create(
                        messages.getMessage(getClass(), "optimisticLock.title"), text)
                .withType(Notifications.Type.WARNING)
                .show());
    }

    /**
     * The message for a failed save: which object, who saved it last and when. Empty when the object cannot be
     * found again or does not record its last modification.
     */
    public Optional<String> describe(@Nullable Throwable throwable) {
        StaleObject stale = staleObject(throwable);
        if (stale == null) {
            return Optional.empty();
        }
        MetaClass metaClass = stale.metaClass();
        MetaProperty modifiedBy = metaClass.findProperty(MODIFIED_BY);
        MetaProperty modifiedDate = metaClass.findProperty(MODIFIED_DATE);
        if (modifiedBy == null || modifiedDate == null) {
            return Optional.empty();
        }
        return dataManager.load(metaClass.getJavaClass()).id(stale.id()).optional()
                .filter(current -> EntityValues.getValue(current, MODIFIED_BY) != null)
                .map(current -> messages.formatMessage(getClass(), "optimisticLock.message",
                        messageTools.getEntityCaption(metaClass),
                        metadataTools.getInstanceName(current),
                        EntityValues.getValue(current, MODIFIED_BY),
                        metadataTools.format(EntityValues.getValue(current, MODIFIED_DATE), modifiedDate)));
    }

    @Nullable
    private StaleObject staleObject(@Nullable Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            Object entity = null;
            if (cause instanceof org.eclipse.persistence.exceptions.OptimisticLockException eclipselink
                    && eclipselink.getQuery() != null) {
                entity = eclipselink.getObject();
            } else if (cause instanceof jakarta.persistence.OptimisticLockException jpa) {
                entity = jpa.getEntity();
            }
            if (entity != null && EntityValues.getId(entity) != null) {
                return new StaleObject(metadata.getClass(entity), EntityValues.getId(entity));
            }
            StaleObject described = fromMessage(cause.getMessage());
            if (described != null) {
                return described;
            }
        }
        return null;
    }

    @Nullable
    private StaleObject fromMessage(@Nullable String message) {
        Matcher matcher = message == null ? null : STALE_OBJECT.matcher(message);
        if (matcher == null || !matcher.find()) {
            return null;
        }
        MetaClass metaClass = metadata.getSession().getClasses().stream()
                .filter(c -> c.getJavaClass().getName().equals(matcher.group(1)))
                .findFirst().orElse(null);
        if (metaClass == null) {
            return null;
        }
        try {
            return new StaleObject(metaClass, UUID.fromString(matcher.group(2)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record StaleObject(MetaClass metaClass, Object id) {
    }
}
