(defproject hasta-la-vista "0.1.0"
  :description ""
  :url ""
  :license {
    :name " Apache License Version 2.0"
    :url "https://www.apache.org/licenses/LICENSE-2.0.txt"
  }
  :dependencies [
    [org.clojure/clojure          "1.6.0"]
    [org.clojure/core.async       "0.1.346.0-17112a-alpha"]
    [org.clojure/tools.cli        "0.3.1"]
    [org.clojure/tools.logging    "0.2.6"]
    ;[com.google.guava/guava       "16.0" ]
    [org.clojure/data.json        "0.2.4"]
    [org.clojure/tools.logging    "0.3.1"]
    [couchbase-clj                "0.2.0"]
    ;[narrator                     "0.1.2"]
  ]
  :exclusions [
    javax.mail/mail
    javax.jms/jms
    com.sun.jdmk/jmxtools
    com.sun.jmx/jmxri
    jline/jline
  ]
  :profiles {
    :uberjar {
      :aot :all
    }
  }
 :jvm-opts [
   "-Xms256m" "-Xmx1024m" "-server" "-XX:MaxPermSize=128m"
   "-XX:NewRatio=2" "-XX:+UseConcMarkSweepGC"
   "-XX:+TieredCompilation" "-XX:+AggressiveOpts"
   "-Dcom.sun.management.jmxremote"
   "-Dcom.sun.management.jmxremote.local.only=false"
   "-Dcom.sun.management.jmxremote.authenticate=false"
   "-Dcom.sun.management.jmxremote.ssl=false"
   ;"-Xprof" "-Xrunhprof"
 ]
  :main hasta-la-vista.core)
