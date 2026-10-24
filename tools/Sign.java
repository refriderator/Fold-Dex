import com.android.apksig.*;import java.io.*;import java.security.*;import java.security.cert.X509Certificate;import java.util.*;
public class Sign{public static void main(String[] a)throws Exception{
 KeyStore ks=KeyStore.getInstance("JKS");try{ks.load(new FileInputStream(a[0]),a[1].toCharArray());}catch(Exception e){ks=KeyStore.getInstance("PKCS12");ks.load(new FileInputStream(a[0]),a[1].toCharArray());}
 String al=ks.aliases().nextElement();PrivateKey k=(PrivateKey)ks.getKey(al,a[1].toCharArray());X509Certificate c=(X509Certificate)ks.getCertificate(al);
 ApkSigner.SignerConfig sc=new ApkSigner.SignerConfig.Builder("deck",k,Collections.singletonList(c)).build();
 new ApkSigner.Builder(Collections.singletonList(sc)).setInputApk(new File(a[2])).setOutputApk(new File(a[3])).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).setMinSdkVersion(28).build().sign();
 System.out.println("signed alias="+al);}}
