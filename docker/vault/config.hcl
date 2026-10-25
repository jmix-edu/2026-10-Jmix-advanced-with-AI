# Vault of the Docker stack (spec 04/02): server mode, data on the vault-data volume.
# Only the compose network reaches it, hence no TLS.
storage "file" {
  path = "/vault/file"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = true
}

api_addr      = "http://vault:8200"
disable_mlock = true
ui            = false
