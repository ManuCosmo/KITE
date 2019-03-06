@echo off
java -cp ../KITE-Engine/target/kite-jar-with-dependencies.jar;../KITE-Common/target/kite-common-1.0-SNAPSHOT.jar;../KITE-Engine-IF/target/kite-if-1.0-SNAPSHOT.jar;target/Janus-test-1.0-SNAPSHOT.jar org.webrtc.kite.Engine configs/local.janus.config.json
