rm -rf keystore.zip keystore.jks

IPS="DNS:tv.exoplayback.wuaki.tv,DNS:exoplayback.wuaki.tv,IP:127.0.0.1"
for j in {0..2}; do
  echo "Range 10.12.10$j.XXX"
  for i in {1..254}; do 
    IPS="$IPS,IP:10.12.10$j.$i"
  done
  echo "Range 192.168.$j.XXX"
  for i in {1..254}; do
    IPS="$IPS,IP:192.168.$j.$i"
  done
done

keytool -genkey -keyalg RSA -alias playbackCA -keystore keystore.jks -providerclass org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath bcprov-ext-jdk15on-157.jar \
  -storetype BKS -validity 9999 \
  -ext SAN=$IPS
zip keystore.zip keystore.jks 
