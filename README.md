# Hasta la vista, baby!

A Clojure library designed to wipe out subset of your data from Couchbase. If you don't know what you are doing, you can wipe out all of your data, so please do not fool around in prod with this tool. Run it as a cron job, with configuration that was tested before and make sure you understand how it works.

## Usage

Running the program is very easy, just start up the JAR (best is to use the uber jar) and the main() function is going to execute the delete on all of the items in a particular view. At this stage only one view is supported. You need to create a view that has the IDs (cannot pass in a null as the id). I suggest you to create a view that has a warning in its name that the items listed are going to be deleted. 

Here is an example view that I use to create a view based on the environment that is sent to us and including only the documents (JSONs) that has something else than 'prod' in their key.

```javasrcipt
function (doc, meta) {
  if(doc.type == 'Batch' && doc.header.env !='prod'){
    emit(meta.id,doc.header.env)
  };
  if(doc.type == 'Session' && doc.env !='prod'){
    emit(meta.id,doc.env)
  };
  
}
```


## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
