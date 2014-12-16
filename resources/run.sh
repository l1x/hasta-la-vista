java -jar hasta-la-vista-0.1.0-standalone.jar \
  -server -Xms256m \
  -Xmx1024m -XX:MaxPermSize=128m \
  -XX:NewRatio=2 -XX:+UseConcMarkSweepGC \
  -XX:+TieredCompilation -XX:+AggressiveOpts
