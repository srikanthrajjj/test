import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Signs (v2; minSdk 26 needs no v1) and verifies an APK:  Sign <in.apk> <out.apk> <keystore> <storepass> <alias> */
public class Sign {
    public static void main(String[] a) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(a[2])) { ks.load(in, a[3].toCharArray()); }
        PrivateKey key = (PrivateKey) ks.getKey(a[4], a[3].toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(a[4]);
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("nest", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(cfg))
                .setInputApk(new File(a[0])).setOutputApk(new File(a[1]))
                .setMinSdkVersion(26).setV1SigningEnabled(false).setV2SigningEnabled(true).build().sign();
        ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[1])).build().verify();
        System.out.println("verified=" + r.isVerified() + " v1=" + r.isVerifiedUsingV1Scheme() + " v2=" + r.isVerifiedUsingV2Scheme());
        for (ApkVerifier.IssueWithParams e : r.getErrors()) System.out.println("ERROR " + e);
        if (!r.isVerified()) System.exit(1);
    }
}
