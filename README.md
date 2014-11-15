# Hasta la vista, baby!

A Clojure library designed to wipe out subset of your data from Couchbase. If you don't know what you are doing, you can wipe out all of your data, so please do not fool around in prod with this tool. Run it as a cron job, with configuration that was tested before and make sure you understand how it works.

## Usage

Running the program is very easy, just start up the JAR (best is to use the uber jar) and the main() function is going to execute the delete on all of the items in a particular view. The name of the view and the connection details are read from the configuration file in the conf/ directory. At this stage only one view is supported. You need to create a view that has the IDs (cannot pass in a null as the id). I suggest you to create a view that has a warning in its name that the items listed are going to be deleted. 

Here is an example view that I use to create a view based on the environment that is sent to us and including only the documents (JSONs) that has something else than 'prod' in their key.

```JavaScript
function (doc, meta) {
  if(doc.type == 'Batch' && doc.header.env !='prod'){
    emit(meta.id,doc.header.env)
  };
  if(doc.type == 'Session' && doc.env !='prod'){
    emit(meta.id,doc.env)
  };
  
}
```

The application configuration file that lives in the conf/ folder using the EDN notation. The configuration section is devided into few sections.



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
