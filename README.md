# Hasta la vista, baby!

A Clojure library designed to wipe out subset of your data from Couchbase. If you don't know what you are doing, you can wipe out all of your data, so please do not fool around in prod with this tool. Run it as a cron job, with configuration that was tested before and make sure you understand how it works.

## Usage

Running the program is very easy, just start up the JAR (best is to use the uber jar) and the 
Main() function is going to execute the delete on all of the items in a particular view. The configuration file is 
in the conf/ directory.The only tunable is the thread pool size (1-50) that is exposed right now in terms of 
concurrency control. The name of the view and the connection details are read from the configuration file. At this stage 
only one view is supported. You need to create a view that has the IDs (cannot pass in a null as the id). I suggest you to 
create a view that has a warning in its name that the items in it are going to be deleted.

### Create a view for deletion

Here is an example to create a view based on the environment in the JSON. 

```JavaScript
function (doc, meta) {
  if(doc.type == 'doc0' && doc.header.env !='prod'){
    emit(meta.id,doc.header.env)
  };
  if(doc.type == 'doc1' && doc.env !='prod'){
    emit(meta.id,doc.env)
  };
  
}
```

### Configure hasta-la-vista


The create-client function[1] is called with the value of :couchbase-client.

1. http://otabat.github.io/couchbase-clj/couchbase-clj.client.html#var-create-client

The configuration file is located in conf/ folder called app.edn.

Example:

```edn
{
  :hasta-la-vista {
    :thread-count 15
  }
  :couchbase-client {
    :bucket "default"
    :uris [
      "http://192.168.67.101:8091/pools"
      "http://192.168.67.102:8091/pools"
      "http://192.168.67.103:8091/pools"
      "http://192.168.67.104:8091/pools"
     ]
    :op-timeout 100000
    :op-queue-max-block-time 30000
    :failure-mode :redistribute
    :max-reconnect-delay 30000
    :obs-poll-interval 100
    :obs-poll-max 400
    :read-buffer-size 32768
    :should-optimize true
    :timeout-exception-threshold 100000
  }
  :couchbase-view {
    :design-document-name "delete_all"
    :view-name "delete-all"
    :query-options {:include-docs true :stale false}
    :batch-size 100
  }
}
```

### Run the deletion


        java -jar hasta-la-vista-0.1.0-standalone.jar

## TODO

1. catch all exceptions in each thread and terminate gracefully
2. expose more CLI options (config file's path, etc.)
3. enable [Metrics](http://metrics-clojure.readthedocs.org/en/latest/)

## License

Copyright 2014 Istvan Szukacs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License
