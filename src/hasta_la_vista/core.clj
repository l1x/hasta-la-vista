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
    [clojure.edn          :as edn     ]
    [clojure.core.async   :as async   ]
    [couchbase-clj.client :as client ]
    [couchbase-clj.query  :as query   ])
  (:import 
    [java.io File])
  (:gen-class))

;; HELPERS

;; Read config

;;https://github.com/l1x/shovel/blob/master/src/shovel/core.clj
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

;Parsing a string to Clojure data structures the safe way
(defn parse-edn-string
  [s]
  (try
    {:ok (clojure.edn/read-string s)}
  (catch Exception e
    {:error "Exception" :fn "parse-config" :exception (.getMessage e)})))

;This function wraps the read-file and the parse-edn-string
;so that it only return {:ok ... } or {:error ...} 
(defn read-config 
  [file]
  (let 
    [ file-string (read-file file) ]
    (cond
      (contains? file-string :ok)
        ;this return the {:ok} or {:error} from parse-edn-string
        (parse-edn-string (file-string :ok))
      :else
        file-string)))

(defn uuid
  "Returns a new java.util.UUID as string" 
  []
  (str (java.util.UUID/randomUUID)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Connecting to CB

(defn connect 
  [cb-client-config]
  (client/defclient client-connection cb-client-config))

(defn exit [] 
  (println "Timeout!")
  (java.lang.System/exit 0))

;; Getting better names for the async functions

(def blocking-producer async/>!!)
(def blocking-consumer async/<!!)

(def non-blocking-producer async/>!)
(def non-blocking-consumer async/<!)


(defn process-message [message] (println "Got a message!" message))

(defn -main 
  "
  This is the main entry point when you start up the JAR
  "
  [& args]
  (let [chan (async/chan)]
    ;;creating 10 async threads
    (dotimes [i 10]
      (async/thread
        (doseq [j (range 2)]
          (Thread/sleep (rand-int 1000))
          (blocking-producer chan (str "Thread id: " i " :: Counter " j)))))
    ;;read 
    (while true 
      (blocking-consumer
        (async/go
          (let [[result source] (async/alts! [chan (async/timeout 1500)])]
            (if (= source chan)
              (println "Got a value!" result)
              ;else - timeout 
              (exit))))))))


