wget https://www.bouncycastle.org/download/bcprov-ext-jdk15on-157.jar

rm -rf keystore.zip keystore.jks

IPS="DNS:exoplayback.wuaki.tv,IP:127.0.0.1"
for i in {1..254}; do 
    IPS="$IPS,IP:10.12.100.$i"
done

keytool -genkey -keyalg RSA -alias playbackCA -keystore keystore.jks \
  -providerclass org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath bcprov-ext-jdk15on-157.jar \
  -storetype BKS -validity 9999 \
  -ext SAN=$IPS

zip keystore.zip keystore.jks 
