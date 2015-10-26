## CORE-NG
core-ng is a webapp framework. it's designed to support our own projects, not as generic web framework

It's still working in progress, so all API is subjected to change. keep in mind

## TODO
* web: think about how to handle content-length=0
* template, use ByteBuffer[] for performance tuning
* async: think about how to support batch with fixed concurrency, or Batch object and chain next easier
* redis: investigate redis hiccup, like 200ms for one operation under load
* web: get/form post, validate bean class and code generation for param serialization?
* real time monitor to ES?
* provide ws interface to send queue message for dev and prod troubleshoot?
* general retry and throttling?
* webservice: client retry on network issue?
* template: "map" support?
* website static content security check, (in server env, this is handled by nginx directly)
* template security check, escaping and etc
* db pool: actively check/close connection before using from transactionManager?
* validator: annotation for website, like @Pattern or @SafeString?
* webservice: @Version to let client pass thru header for action log?
* cm: config management, dynamic update properties?
* cache: advanced feature: local/remote 2 level, async get on expiration, pre warm?
* db: batch insert/update auto convert to small batch like 3000?
* web: rewrite undertow built-in form/multi part parser for seamless integration and exception handling?
* review/refactory/unit-test all packages (final/encapsulation/etc)

## Change log
please check [CHANGELOG.md](CHANGELOG.md)

