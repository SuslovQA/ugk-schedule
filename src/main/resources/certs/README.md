# MAX TLS trust anchor

Downloaded on 2026-09-18 from the Gosuslugi certificate distribution:
https://gu-st.ru/content/Other/doc/russian_trusted_root_ca.cer

Official installation page: https://www.gosuslugi.ru/crt
MAX requirement: https://dev.max.ru/docs-api

Subject/issuer: Russian Trusted Root CA.
Valid until 2032-02-28.
SHA-256: D26D2D0231B7C39F92CC738512BA54103519E4405D68B5BD703E9788CA8ECF31

Only the MAX HTTP client uses this root. Hostname and certificate verification
remain enabled. No OS or global JDK trust stores are modified.
When rotating this certificate, download its replacement from the official
distribution over verified HTTPS, inspect it and run the opt-in TLS smoke test:
`mvn -Dmax.tls.smoke=true -Dtest=MaxHttpConfigTest test`.
