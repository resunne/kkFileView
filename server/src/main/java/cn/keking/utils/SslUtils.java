package cn.keking.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.springframework.util.StringUtils;

import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author 鞠玉果
 */
@Slf4j
public class SslUtils {

    private static void trustAllHttpsCertificates() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[1];
        TrustManager tm = new miTM();
        trustAllCerts[0] = tm;
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }

    static class miTM implements TrustManager, X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
        }
    }

    /**
     * 忽略HTTPS请求的SSL证书，必须在openConnection之前调用
     */
    public static void ignoreSsl() throws Exception {
        HostnameVerifier hv = (urlHostName, session) -> true;
        trustAllHttpsCertificates();
        HttpsURLConnection.setDefaultHostnameVerifier(hv);
    }

    public static SSLConnectionSocketFactory getSslFactory() {
        SSLConnectionSocketFactory sslFactory = null;
        try {
            TrustStrategy acceptingTrustStrategy = new TrustSelfSignedStrategy();
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
            sslFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        } catch (Exception e) {
            log.error(String.format("获取 SSL 工厂失败：%s。", StringUtils.hasText(e.getMessage()) ? e.getMessage() : "发生错误"), e);
        }
        return sslFactory;
    }

}
