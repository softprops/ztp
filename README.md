# ztp

> an http transport layer for zookeeper

(wip)

# the why

zookeeper is a distributed, consistent key value store for shared configuration and service discovery.

Unfortunately, in order to consume this data a client is reponsible for maintaining a persistent session with a zookeeper
server in order to send and receive messages. The driver interface is not always straight forward to use and reason about its network behavior.
Tooling around making this interface nicer to use exists but wrappers still require clients carry the weight of maintaining this persistent connection when often times all a client will want to do is to read a single piece of information from the server. To worsen the situation, the same in process connection handling may need to be re-implemented in heterogeneous environments.

# the what

As far as transport layers go, there is one such protocol that is ubiquitously supported across all platforms for request/respond type network interactions, HTTP. This library provides an bridge between both worlds. An embedded server can be run on a shared host, lessening the overall connections a zookeeper needs to handle, and proxy zookeeper information over HTTP.

# the how

At its heart, zookeeper stores data at paths and information about sub directories underneath those paths. Each segment of a path also carries a bit
of metadata with it. These properties tend to map well with the semantics of HTTP. In HTTP, data is also stored at paths. Additional data may be stored under extentions to those paths ([links](https://tools.ietf.org/html/rfc5988)). Metadata about the data that exists at a path is encoded in HTTP headers.

Ztp aims to map those properties directly. Data stored within a ZNode at a given path can be resolved over HTTP at an HTTP path. By default this data is returned in (urlsafe) base64 encoding. The accept header may be used to coerse this into a utf8 string or json. ZNode metadata are encoded in `X-ZNode-*` HTTP headers. ZNode children are represented as HTTP Link headers.



Doug Tangren (softprops) 2015
