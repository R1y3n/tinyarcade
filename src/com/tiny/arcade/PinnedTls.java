package com.tiny.arcade;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Small platform-TLS wrapper that adds SPKI pins after normal certificate validation. */
public final class PinnedTls {
    private static final String[] RELEASE_ASSET_PINS = {
        "lNuax9RNol8Xx1GETur9frjXt+himsZHCLKOginvFyw=", // GitHub release asset edge
        "LoMHBotttiDko50Gi13uXW71eIy7LAttI+rYT8wXF4w=", // Let's Encrypt YR1
        "fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88="  // ISRG Root YR
    };
    private static final String[] FIREBASE_PINS = {
        "GCcSISJfxoi/efNtgJJf9PIBy99dfmaA59y/dH2niKs=", // Firebase RTDB leaf (backup)
        "yDu9og255NN5GEf+Bwa9rTrqFQ0EydZ0r1FCh9TdAW4=", // Google Trust Services WR1
        "hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=", // GTS Root R1
        "Vfd95BwDeSQo+NUYxVEEIlvkOlWY2SalKK1lPhzOx78=", // GTS Root R2
        "QXnt2YHvdHR3tJYmQIr0Paosp6t/nggsEGD4QJZ3Q0g=", // GTS Root R3
        "mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="  // GTS Root R4
    };
    private static final String[] AUTH_PINS = {
        "pKUswmsFFcpKtaeEnEPUymNTYIK8T8lCmCrm5mwK1N8=", // Google auth leaf (backup)
        "vh78KSg1Ry4NaqGDV10w/cTb9VH3BQUZoCWNa93W/EY=", // GTS WE2
        "mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=", // GTS Root R4
        "hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=", // GTS Root R1
        "Vfd95BwDeSQo+NUYxVEEIlvkOlWY2SalKK1lPhzOx78="  // GTS Root R2
    };
    private static final String[] GITHUB_PINS = {
        "P5437A6f5e2OnunPOzNhRV2iWeOE1XGTN9xskQ53yTk=", // GitHub API leaf
        "8gWWkWa8AfAz0khtRYRodss7ieQPpu8Sd7DppK94eVo=", // github.com leaf
        "lNuax9RNol8Xx1GETur9frjXt+himsZHCLKOginvFyw=", // GitHub release asset edge
        "ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY=", // Sectigo E36
        "sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE="  // UserTrust ECC root
    };

    private PinnedTls() {
    }

    public static void configure(HttpsURLConnection connection, String host)
        throws java.io.IOException {
        try {
            configurePinned(connection, host);
        } catch (java.io.IOException e) {
            throw e;
        } catch (Exception e) {
            throw new java.io.IOException("Could not configure pinned TLS", e);
        }
    }

    private static void configurePinned(HttpsURLConnection connection, String host) throws Exception {
        String[] pins;
        if ("release-assets.githubusercontent.com".equalsIgnoreCase(host)) {
            pins = RELEASE_ASSET_PINS;
        } else if ("api.github.com".equalsIgnoreCase(host)
            || "github.com".equalsIgnoreCase(host)
            || host.toLowerCase(java.util.Locale.US).endsWith(".githubusercontent.com")) {
            pins = GITHUB_PINS;
        } else if ("identitytoolkit.googleapis.com".equalsIgnoreCase(host)
            || "securetoken.googleapis.com".equalsIgnoreCase(host)) {
            pins = AUTH_PINS;
        } else if (host.toLowerCase(java.util.Locale.US).endsWith("firebasedatabase.app")
            || host.toLowerCase(java.util.Locale.US).endsWith("firebaseio.com")) {
            pins = FIREBASE_PINS;
        } else {
            throw new javax.net.ssl.SSLPeerUnverifiedException("Unpinned HTTPS host");
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        factory.init((java.security.KeyStore) null);
        TrustManager[] managers = factory.getTrustManagers();
        X509TrustManager platform = null;
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) {
                platform = (X509TrustManager) manager;
                break;
            }
        }
        if (platform == null) throw new IllegalStateException("Platform trust manager unavailable");
        final X509TrustManager delegate = platform;
        final String[] acceptedPins = pins;
        X509TrustManager pinned = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
                delegate.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
                delegate.checkServerTrusted(chain, authType);
                try {
                    for (X509Certificate cert : chain) {
                        byte[] encoded = cert.getPublicKey().getEncoded();
                        String pin = android.util.Base64.encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(encoded),
                            android.util.Base64.NO_WRAP);
                        for (String accepted : acceptedPins) {
                            if (accepted.equals(pin)) return;
                        }
                    }
                } catch (Exception e) {
                    throw new java.security.cert.CertificateException("Could not verify TLS pin", e);
                }
                throw new java.security.cert.CertificateException("TLS public-key pin mismatch");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return delegate.getAcceptedIssuers();
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{pinned}, null);
        connection.setSSLSocketFactory(context.getSocketFactory());
    }
}
