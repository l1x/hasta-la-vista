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
    [clojure.edn            :as edn                 ]
    [clojure.core.async     :refer 
      [alts! chan go thread timeout >! >!! <! <!!]  ]
    [couchbase-clj.client   :as client              ]
    [couchbase-clj.query    :as query               ]
    [clojure.tools.logging  :as log                 ])
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
  (let [  ^clojure.core.async.impl.channels.ManyToManyChannel chan                  (chan 5) 
          ^clojure.lang.PersistentHashMap                     config                (read-config "conf/app.edn") ]
    ;; INIT
    (log/info "init :: start")
    (log/info "checking config...")
    (cond 
      (contains? config :ok)
        (config-ok config)
      :else
        (config-err config))
    ;; Initializing CB connections
    (let [  ^clojure.lang.PersistentHashMap           client-config         (get-in config [:ok :couchbase-client])
            ^couchbase_clj.client.CouchbaseCljClient  client                (connect client-config)
            ^clojure.lang.PersistentArrayMap          view-config           (get-in config [:ok :couchbase-view])
            ^java.lang.String                         design-document-name  (:design-document-name view-config)
            ^java.lang.String                         view-name             (:view-name view-config)
            ^clojure.lang.PersistentHashMap           query-options         (:query-options view-config)
            ^java.lang.Long                           batch-size            (:batch-size view-config)  
                                                      thread-count          (get-in config [:ok :hasta-la-vista :thread-count])
                                                      thread-wait           (get-in config [:ok :hasta-la-vista :thread-wait])
                                                      channel-timeout       (get-in config [:ok :hasta-la-vista :channel-timeout]) ]
    ;; (println (client/get-client-status client))
    (log/info (client/get-available-servers client))
    ;; creating N async threads
    (dotimes [i thread-count]
      (thread
        ;; inside a thread we usually process something 
        ;; blocking like this thread sleeps for random(1000) ms
        ;; after the blocking part you send the message
        ;; :couchbase-client might be missing - should catch it here
        ;; connecting might be slow
        (Thread/sleep thread-wait)
        ;; should be a check here if connection was successful 
        ;; (cond ...
        ;; "Lazy query can be used to get the amount of documents specified per iteration. 
        ;; Communication between the client and Couchbase server will only occur per iteration.
        ;; This is typically used to get a large data lazily."
        ;; Example query-options
        ;; {:include-docs true :desc false :startkey-doc-id :doc1 :endkey-doc-id :doc2
        ;;   :group true :group-level 1 :inclusive-end false :key :key1 :limit 100 :range [:start-key :end-key]
        ;;   :range-start :start-key2 :range-end :end-key2 :reduce false :skip 1 :stale false:on-error :continue}
        (doseq [r (client/lazy-query client design-document-name view-name query-options batch-size)]
          (let [ids (map client/view-id r)]
            ;; delete all the keys, using the same connection as 
            ;; for the read
            (doall (pmap #(client/delete client %) ids))
            ;; blocking producer should have a timeout here
            (blocking-producer chan (str "ID: " (first ids) " Batch size: " batch-size))))
          ;after we are done
          (client/shutdown client))))
    ;; read while there is value
    ;; or recur
    (while true 
      (blocking-consumer
        (go
          (let [[result source] (alts! [chan (timeout channel-timeout)])]
            ;; if source is the channel than a value is returned in result
            ;; when the source is something else like timeout, we time out
            ;; and stop execution
            ;; production timeout should be like 1-3 mins
            (if (= source chan)
              (log/info "Got a value!" result)
              ;else - timeout 
              (do 
                (log/info "Channel timed out. Stopping...") 
                (exit 0)))))))))
;; END
