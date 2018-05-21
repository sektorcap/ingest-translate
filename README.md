# Elasticsearch translate Ingest Processor

A general search and replace tool that uses a file to determine replacement values.
Currently, this processor supports only YAML files.

Operationally, if the event field specified in the `field` configuration matches the EXACT contents of a dictionary entry key, the field’s value will be substituted with the matched key’s value from the dictionary.

By default, the processor will replace the contents of the matching event field (in-place). However, by using the `target_field` configuration item, you may also specify a target event field to populate with the new translated value.

For each dictonary file defined in a pipeline through the processor `translate`, a thread will check periodically
the changes on the file.

Note: the key dictionary check is case-insensitive.


## Anonymize Options
| Name | Required | Default | Description |
|------|----------|---------|-------------|
|`field`|yes|-|The name of the event field containing the value to be compared for a match.|
|`target_field`|no|`field`|The destination field you wish to populate with the translated value. If not defined `field` will be overwritten.|
|`dictionary`|yes|-|The filename containng the dictionary. The file must be present in the `ingest-translate` configuration direcotry|
|`add_to_root`|no|false|Flag that forces the serialized `YAML` item to be injected into the top level of the document. target_field must not be set when this option is chose.|
|`ignore_missing`|no|false|If `true` and `field` does not exist, the processor quietly exits without modifying the document|

## Usage

```
PUT _ingest/pipeline/translate-pipeline
{
  "description": "A pipeline to do whatever",
  "processors": [
    {
      "translate" : {
        "field"          : "my_field",
        "target_field"   : "target",
        "dictionary"     : "dictionary-test1.yml"
      }
    }
  ]
}

PUT /my-index/my-type/1?pipeline=translate-pipeline
{
  "my_field" : "100.11.12.193"
}

GET /my-index/my-type/1
{
  "_index": "my-index",
  "_type": "my-type",
  "_id": "1",
  "_version": 1,
  "found": true,
  "_source": {
    "my_field": "100.11.12.193",
    "target": "tor exit node"
  }
}


PUT _ingest/pipeline/translate-pipeline
{
  "description": "A pipeline to do whatever",
  "processors": [
    {
      "translate" : {
        "field"          : "my_field",
        "dictionary"     : "dictionary-test1.yml"
      }
    }
  ]
}

PUT /my-index/my-type/2?pipeline=translate-pipeline
{
  "my_field" : "100.11.12.193"
}

GET /my-index/my-type/2
{
  "_index": "my-index",
  "_type": "my-type",
  "_id": "2",
  "_version": 1,
  "found": true,
  "_source": {
    "my_field": "tor exit node"
  }
}


PUT _ingest/pipeline/translate-pipeline
{
  "description": "A pipeline to do whatever",
  "processors": [
    {
      "translate" : {
        "field"          : "my_field",
        "target_field"   : "target_complex",
        "dictionary"     : "dictionary-test2.yml"
      }
    }
  ]
}

PUT /my-index/my-type/3?pipeline=translate-pipeline
{
  "my_field" : "ldap"
}

GET /my-index/my-type/3
{
  "_index": "my-index",
  "_type": "my-type",
  "_id": "3",
  "_version": 1,
  "found": true,
  "_source": {
    "my_field": "ldap",
    "target_complex": {
      "host": "server1",
      "port": 636,
      "base": "dc=example,dc=local",
      "attribute": "uid",
      "ssl": true,
      "allowed_groups": [
        "group1",
        "group2",
        "group3"
      ]
    }
  }
}

PUT _ingest/pipeline/translate-pipeline
{
  "description": "A pipeline to do whatever",
  "processors": [
    {
      "translate" : {
        "field"          : "my_field",
        "add_to_root"    : true,
        "dictionary"     : "dictionary-test2.yml"
      }
    }
  ]
}

PUT /my-index/my-type/4?pipeline=translate-pipeline
{
  "my_field" : "ldap"
}

GET /my-index/my-type/4
{
  "_index": "my-index",
  "_type": "my-type",
  "_id": "4",
  "_version": 1,
  "found": true,
  "_source": {
    "port": 636,
    "host": "server1",
    "allowed_groups": [
      "group1",
      "group2",
      "group3"
    ],
    "my_field": "ldap",
    "attribute": "uid",
    "ssl": true,
    "base": "dc=example,dc=local"
  }
}

PUT _ingest/pipeline/translate-caseinsensitive-pipeline
{
  "description": "A pipeline to do whatever",
  "processors": [
    {
      "translate" : {
        "field"          : "my_field",
        "target_field"   : "my_target",
        "dictionary"     : "dictionary-test2.yml"
      }
    }
  ]
}

PUT /my-index/my-type/5?pipeline=translate-caseinsensitive-pipeline
{
  "my_field" : "TEST3"
}

GET /my-index/my-type/5
{
  "_index": "my-index",
  "_type": "my-type",
  "_id": "5",
  "_version": 1,
  "found": true,
  "_source": {
    "my_field": "TEST3",
    "my_target": "TeSt3"
  }
}

```

## Configuration
In `elasticsearch.yml` configuration file you can set the cron expression in [Quartz](http://quartz-scheduler.org/)
[format](http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html) to define
when to check the changes on the dictionary files.
```
ingest.translate.cron_check: "* 0 * * * ?"
```
The default value is `"* 0 * * * ?"` (every hour at minute 0).


## Setup
Remember to set the `elasticsearchVersion` parameter in your `gradle.properties` file.

In order to install this plugin, you need to create a zip distribution first by running

```bash
gradle clean check
```
This will produce a zip file in `build/distributions`.

After building the zip file, you can install it like this

```bash
bin/elasticsearch-plugin install file:///path/to/ingest-translate/build/distribution/ingest-translate-x.y.z.zip
```

## Java
Starting from `elasticsearch 6.2`, developers must use Java 9.

## Bugs & TODO

* There are always bugs
* and todos...

## Acknowledgements
Thanks to [Alexander Reelsen](https://github.com/spinscale) for his project
https://github.com/spinscale/cookiecutter-elasticsearch-ingest-processor.
