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
    [clojure.edn            :as edn         ]
    [clojure.core.async     :refer 
      [alts! chan go thread timeout 
       >! >!! <! <!! go-loop  ]             ]
    [couchbase-clj.client   :as client      ]
    [couchbase-clj.query    :as query       ]
    [clojure.tools.logging  :as log         ]
    ;[narrator.operators     :as narr-ops    ]
    ;[narrator.query         :as narr-query  ]
    )
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
  (client/defclient client-connection cb-client-config)
  ;return
  client-connection)

(defn exit [n] 
  (log/info "init :: stop")
  (java.lang.System/exit n))

;; Getting better names for the async functions

(def blocking-producer >!!)
(def blocking-consumer <!!)

(def non-blocking-producer >!)
(def non-blocking-consumer <!)


(defn process-message [message] (log/info "Got a message!" message))

(defn config-ok [config]
  (log/info "config [ok]") 
  (log/info config))

(defn config-err [config]
  (log/error "config [error]") 
  (log/error config)
  (exit 1))

;; MAIN

(defn -main
  "This is the main entry point when you start up the JAR
  parses the config, starts up the connections in each thread 
  and starts to consume the view by large chunks. There is an assumption 
  that CB returns the right set of keys and the view update works properly
  
  There is a query parameter that can be passed: stale 
  <quote>If stale=ok is set, Couchbase will not refresh the view even if it 
  is stale. The benefit of this   is a an improved query latency. If stale=update_after is set, 
  Couchbase will update the view after the stale result is returned. If stale=false is set, 
  Couchbase will refresh the view and return you most updated results.<quote>

  https://forums.couchbase.com/t/how-does-stale-query-work/870"
  [& args]
  (let [  ^clojure.core.async.impl.channels.ManyToManyChannel stat-chan (chan)
          ^clojure.core.async.impl.channels.ManyToManyChannel work-chan (chan)
          ^clojure.lang.PersistentHashMap                     config    (read-config "conf/app.edn") ]
    ;; INIT
    (log/info "init :: start")
    (log/info "checking config...")
    (cond 
      (contains? config :ok)
        (config-ok config)
      :else
        ;; exit 1 here
        (config-err config))
    ;; Initializing CB connections
    (let [  ^clojure.lang.PersistentHashMap           client-config         (get-in config [:ok :couchbase-client])
            ^couchbase_clj.client.CouchbaseCljClient  client                (connect client-config)
            ^clojure.lang.PersistentArrayMap          view-config           (get-in config [:ok :couchbase-view])
            ^java.lang.String                         design-document-name  (:design-document-name view-config)
            ^java.lang.String                         view-name             (:view-name view-config)
            ^clojure.lang.PersistentHashMap           query-options         (:query-options view-config)
            ^java.lang.Long                           batch-size            (:batch-size view-config)  
            ^java.lang.Long                           thread-count          (get-in config [:ok :hasta-la-vista :thread-count])
            ^java.lang.Long                           thread-wait           (get-in config [:ok :hasta-la-vista :thread-wait]) ]

      (log/info (client/get-available-servers client))

      ;; creating N async threads
      (dotimes [i thread-count]
        (thread
          (let [ ^couchbase_clj.client.CouchbaseCljClient  client-del (connect client-config) ]
            (Thread/sleep thread-wait)
              (go-loop []
                (let [  ids       (blocking-consumer work-chan) 
                        start     (. java.lang.System (clojure.core/nanoTime)) 
                        _         (doall (pmap #(client/delete client-del %) ids))
                        exec-time (with-precision 3 (/ (- (. java.lang.System (clojure.core/nanoTime)) start) 1000000.0))
                        perf      (/ (count ids) exec-time) ]
                  ;; send results to stat-chan
                  (blocking-producer stat-chan {:thread-name (.getName (Thread/currentThread)) :first_id (first ids) :time exec-time :perf perf})
                  (recur))))))

      ;; reading stat-chan
      (thread (go-loop [] (let [stat (blocking-consumer stat-chan)] (log/info stat)) (recur)))

      ;; send in all of the ids batch-size amount a time
      (doseq [r (client/lazy-query client design-document-name view-name query-options batch-size)]
        (let [ids (map client/view-id r)]
          (blocking-producer work-chan ids)))

      ;; shutting down the client & the main thread
      (client/shutdown client)
      (exit 0)

    ;; end main
    )))

;;END
