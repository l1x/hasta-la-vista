;;Copyright 2014 Istvan Szukacs

;;Licensed under the Apache License, Version 2.0 (the "License");
;;you may not use this file except in compliance with the License.
;;You may obtain a copy of the License at

;;    http://www.apache.org/licenses/LICENSE-2.0

;;Unless required by applicable law or agreed to in writing, software
;;distributed under the License is distributed on an "AS IS" BASIS,
;;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;;See the License for the specific language governing permissions and
;;limitations under the License

(ns hasta-la-vista.core
  (:require
    [clojure.edn          :as edn                 ]
    [clojure.core.async   :refer 
      [alts! chan go thread timeout >! >!! <! <!!  ]]
    [couchbase-clj.client :as client              ]
    [couchbase-clj.query  :as query               ])
  (:import 
    [java.io File])
  (:gen-class))

;; HELPERS

;; Read config

;;https://github.com/l1x/shovel/blob/master/src/shovel/core.clj
;;this is considered defensing programming, but it is intentional
;;supplying an arbitrary bad input should not cause this function to 
;;explode, in fact it should just return an error
(defn read-file
  "Returns {:ok string } or {:error...}"
  [^String file]
  (try
    (cond
      (.isFile (File. file))
        {:ok (slurp file) }                         ; if .isFile is true {:ok string}
      :else
        (throw (Exception. "Input is not a file"))) ;the input is not a file, throw exception
  (catch Exception e
    {:error "Exception" :fn "read-file" :exception (.getMessage e) }))) ; catch all exceptions

;;Parsing a string to Clojure data structures the safe way
;;aka what could possibly go wrong dealing with a random
;;user controlled string
(defn parse-edn-string
  "Returns the Clojure data structure representation of s"
  [s]
  (try
    {:ok (clojure.edn/read-string s)}
  (catch Exception e
    {:error "Exception" :fn "parse-config" :exception (.getMessage e)})))

;This function wraps the read-file and the parse-edn-string
;so that it only return {:ok ... } or {:error ...} 
(defn read-config
  "Returns the Clojure data structure version of the config file"
  [file]
  (let 
    [ file-string (read-file file) ]
    (cond
      (contains? file-string :ok)
        ;this return the {:ok} or {:error} from parse-edn-string
        (parse-edn-string (file-string :ok))
      :else
        ;the read-file operation returned an error
        file-string)))

(defn uuid
  "Returns a new java.util.UUID as string" 
  []
  (str (java.util.UUID/randomUUID)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Connecting to CB

(defn connect
  "Returns a client that is connected to the Couchbase cluster
   using multiple threads"
  [cb-client-config]
  (client/defclient client-connection cb-client-config))

(defn exit [n] 
  (println "init :: stop")
  (java.lang.System/exit n))

;; Getting better names for the async functions

(def blocking-producer >!!)
(def blocking-consumer <!!)

(def non-blocking-producer >!)
(def non-blocking-consumer <!)


(defn process-message [message] (println "Got a message!" message))

(defn config-ok [config]
  (println "config [ok]") 
  (println config))

(defn config-err [config]
  (println "config [error]") 
  (println config)
  (exit 1))

(defn -main 
  "
  This is the main entry point when you start up the JAR
  parses the config, starts up the connections in each thread 
  and starts to consume the view by 1000 item chunks
  
  "
  [& args]
  (let [  chan    (chan 5) 
          config  (read-config "conf/app.edn") ]
    ;; INIT
    (println "init :: start")
    (println "checking config...")
    (cond 
      (contains? config :ok)
        (config-ok config)
      :else
        (config-err config))
    ;; creating 10 async threads
    (dotimes [i 10]
      (thread
        ;; inside a thread we usually process something 
        ;; blocking like this thread sleeps for random(1000) ms
        ;; after the blocking part you send the message
        (doseq [j (range 2)]
          (Thread/sleep (rand-int 1000))
          (blocking-producer chan (str "Thread id: " i " :: Counter " j)))))
    ;; read while there is value
    ;; or 
    (while true 
      (blocking-consumer
        (go
          (let [[result source] (alts! [chan (timeout 1500)])]
            ;; if source is the channel than a value is returned in result
            ;; when the source is something else like timeout, we time out
            ;; and stop execution
            ;; production timeout should be like 1-3 mins
            (if (= source chan)
              (println "Got a value!" result)
              ;else - timeout 
              (exit 0))))))))


