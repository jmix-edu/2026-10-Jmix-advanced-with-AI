package com.company.timesheets.security;

import io.jmix.core.JmixSecurityFilterChainOrder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Эта конфигурация дополняет стандартные конфигурации безопасности из модулей Jmix (security-flowui, oidc,
 * authserver).
 * <p>
 * Безопасность собственных API-эндпоинтов настраивается бинами {@link SecurityFilterChain} в этом классе.
 * Обычно собственный SecurityFilterChain должен применяться первым, поэтому бину нужно задать подходящий
 * {@link org.springframework.core.annotation.Order}. Значение порядка из
 * {@link io.jmix.core.JmixSecurityFilterChainOrder#CUSTOM} гарантированно меньше порядка любой другой цепочки
 * фильтров Jmix.
 * <p>
 * Пример:
 *
 * <pre>
 * &#064;Bean
 * &#064;Order(JmixSecurityFilterChainOrder.CUSTOM)
 * SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
 *     http.securityMatcher("/public/**")
 *             .authorizeHttpRequests(authorize ->
 *                     authorize.anyRequest().permitAll()
 *             );
 *     return http.build();
 * }
 * </pre>
 *
 * @see io.jmix.securityflowui.security.FlowuiVaadinWebSecurity
 */
@Configuration
public class TimesheetsSecurityConfiguration {

    @Bean
    @Order(JmixSecurityFilterChainOrder.CUSTOM)
    SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/public/**")
                .authorizeHttpRequests(authorize ->
                        authorize.anyRequest().permitAll()
                );

        return http.build();
    }

    // Actuator: health и info без входа, все остальные эндпоинты закрыты для всех (спека 04/01).
    // Пути, а не EndpointRequest: он находится в spring-boot-security, который Jmix не использует.
    @Bean
    @Order(JmixSecurityFilterChainOrder.CUSTOM)
    SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator", "/actuator/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().denyAll());

        return http.build();
    }
}