# Elasticsearch translate Ingest Processor

A general search and replace tool that uses a file to determine replacement values.
Currently, this processor supports only YAML files.

Operationally, if the event field specified in the `field` configuration matches the contents of a dictionary entry key
the field’s value will be substituted with the matched key’s value from the dictionary.

By default, the processor will replace the contents of the matching event field (in-place). However, by using the `target_field` configuration item, you may also specify a target event field to populate with the new translated value.

For each dictionary file defined in a pipeline through the processor `translate`, a thread will check periodically
the changes on the file.

At the moment the processor supports 2 translators: `String Translator` and `Ip Translator`.

## String Translator
This is the translator used by default. It treats the dictionary keys as `string` case-insensitive.

The multiple match is not allowed.

## Ip Translator
It treats the dictionary keys as `subnet`. The dictionary keys must be written in `cidr` notation.

The multiple match is allowed in order to support subnets overlapping.


## Translate Options
| Name | Required | Default | Description |
|------|----------|---------|-------------|
|`field`|yes|-|The name of the event field containing the value to be compared for a match.|
|`target_field`|no|`field`|The destination field you wish to populate with the translated value. If not defined `field` will be overwritten.|
|`dictionary`|yes|-|The filename containg the dictionary. The file must be present in the `ingest-translate` configuration direcotry|
|`type`|no|`string`|The translator type (`string` or `ip`)|
|`multiple_match`|no|`false`|If `true` allows multiple match on the dictionary (used only for `Ip Translator`)|
|`add_to_root`|no|`false`|Flag that forces the serialized `YAML` item to be injected into the top level of the document. target_field must not be set when this option is chose.|
|`ignore_missing`|no|`false`|If `true` and `field` does not exist, the processor quietly exits without modifying the document|

## Usage with `String Translator`

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

PUT /my-index/_doc/1?pipeline=translate-pipeline
{
  "my_field" : "100.11.12.193"
}

GET /my-index/_doc/1
{
  "_index": "my-index",
  "_type": "_doc",
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

PUT /my-index/_doc/2?pipeline=translate-pipeline
{
  "my_field" : "100.11.12.193"
}

GET /my-index/_doc/2
{
  "_index": "my-index",
  "_type": "_doc",
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

PUT /my-index/_doc/3?pipeline=translate-pipeline
{
  "my_field" : "ldap"
}

GET /my-index/_doc/3
{
  "_index": "my-index",
  "_type": "_doc",
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

PUT /my-index/_doc/4?pipeline=translate-pipeline
{
  "my_field" : "ldap"
}

GET /my-index/_doc/4
{
  "_index": "my-index",
  "_type": "_doc",
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

PUT /my-index/_doc/5?pipeline=translate-caseinsensitive-pipeline
{
  "my_field" : "TEST3"
}

GET /my-index/_doc/5
{
  "_index": "my-index",
  "_type": "_doc",
  "_id": "5",
  "_version": 1,
  "found": true,
  "_source": {
    "my_field": "TEST3",
    "my_target": "TeSt3"
  }
}

```

## Usage with `Ip Translator`

```
PUT _ingest/pipeline/translate-pipeline
{
  "description": "A pipeline to do whatever",
  "processors": [
    {
      "translate" : {
        "field"          : "my_field",
        "target_field"   : "target",
        "dictionary"     : "dictionary-test3.yml",
        "type": "ip"
      }
    }
  ]
}

PUT /my-ipindex/_doc/1?pipeline=translate-pipeline
{
  "my_field" : "13.120.128.5"
}

GET /my-ipindex/_doc/1
{
  "_index": "my-ipindex",
  "_type": "_doc",
  "_id": "1",
  "_version": 1,
  "found": true,
  "_source": {
    "my_field": "13.120.128.5",
    "target": "WI-FI"
  }
}


PUT _ingest/pipeline/translate-pipeline
{
  "description": "A pipeline to do whatever",
  "processors": [
    {
      "translate" : {
        "field"          : "my_field",
        "target_field"   : "complex_field",
        "dictionary"     : "dictionary-test3.yml",
        "type": "ip"
      }
    }
  ]
}

PUT /my-ipindex/_doc/2?pipeline=translate-pipeline
{
  "my_field" : "10.11.28.128"
}

GET /my-ipindex/_doc/2
{
  "_index": "my-ipindex",
  "_type": "_doc",
  "_id": "2",
  "_version": 1,
  "found": true,
  "_source": {
    "my_field": "10.11.28.128",
    "complex_field": {
      "gateway": "gw.lab2.it",
      "label": "Ingest Lab 2"
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
        "target_field"   : "multiple_field",
        "dictionary"     : "dictionary-test3.yml",
        "multiple_match" : true,
        "type"           : "ip"
      }
    }
  ]
}


PUT /my-ipindex/_doc/3?pipeline=translate-pipeline
{
  "my_field" : "10.10.22.1"
}

GET /my-ipindex/_doc/3
{
  "_index": "my-ipindex",
  "_type": "_doc",
  "_id": "3",
  "_version": 1,
  "found": true,
  "_source": {
    "multiple_field": [
      {
        "label": "Internal Net"
      },
      {
        "label": "Ingest Lab 1"
      },
      {
        "host": "gw.lab1.it",
        "label": "GW for Ingest Lab 1"
      }
    ],
    "my_field": "10.10.22.1"
  }
}


PUT _ingest/pipeline/translate-pipeline
{
  "description": "A pipeline to do whatever",
  "processors": [
    {
      "translate" : {
        "field"          : "my_field",
        "target_field"   : "multiple_field",
        "dictionary"     : "dictionary-test3.yml",
        "multiple_match" : false,
        "type"           : "ip"
      }
    }
  ]
}

PUT /my-ipindex/_doc/4?pipeline=translate-pipeline
{
  "my_field" : "10.10.22.1"
}

GET /my-ipindex/_doc/4
{
  "_index": "my-ipindex",
  "_type": "_doc",
  "_id": "4",
  "_version": 1,
  "found": true,
  "_source": {
    "multiple_field": {
      "label": "Internal Net"
    },
    "my_field": "10.10.22.1"
  }
}
```



## Configuration
In `elasticsearch.yml` configuration file you can set the cron expression in [Quartz](http://quartz-scheduler.org/)
[format](http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-06.html) to define
when to check the changes on the dictionary files.
```
ingest.translate.cron_check: "* 0 * * * ?"
```
The default value is `"* 0 * * * ?"` (every hour at minute 0).


## Setup
Remember to set the `elasticsearchVersion` parameter in your `gradle.properties` file.

In order to install this plugin, you need to create a zip distribution first by running.

```bash
gradle clean check
```
This will produce a zip file in `build/distributions`.

After building the zip file, you can install it like this.

```bash
bin/elasticsearch-plugin install file:///path/to/ingest-translate/build/distribution/ingest-translate-x.y.z.zip
```

## Java
Starting from `elasticsearch 7.x`, developers must use Java 14.

## Bugs & TODO

* There are always bugs
* and todos...

## Acknowledgements
Thanks to [Alexander Reelsen](https://github.com/spinscale) for his project
https://github.com/spinscale/cookiecutter-elasticsearch-ingest-processor.
