# Depends

Depends is a library intended to do queue re-ordering based on read/write
dependencies, not the reliable brand for underware, pads, and protection. This
library utilizes Manifold for stream abstraction and for asyncronous workflows
in order to maximize compatibility with any of the abstractions that implement
Manifold sources and/or sinks.

This library is utilizing Clojure 1.9 (alpha 10), clojure.spec and
clojure.test.check.

## Usage

It's on Clojars!

[![Clojars Project](https://img.shields.io/clojars/v/org.vlacs/depends.svg)](https://clojars.org/org.vlacs/depends)

Depends is fairly straight forward, there are only a handful (so far,) of functions
that you should be thinking about when using Depends:

First, there is ```depends/dependify``` which creates a single instance of
a dependency manager. Its argument are an input (anything sourceable by
manifold,) and output (anything sinkable by Manifold,) and a map describing the
options to be applied. Its output is a map of the internals for that instance.

```clj
(def input (manifold.stream/stream 10))
(def output (manifold.stream/stream 10))
(depends/dependify input output {:max-waiting 10 :clean-up-interval 2500})
```

However, you don't need to use Manifold streams for your abstractions. Manifold
supports several kinds of abstractions out of the box and core.async channels is
one of them.

```clj
(def input (clojure.core.async/chan 10))
(def output (clojure.core.async/chan 10))
(depends/dependify input output)
```

Currently, the option map for ```dependify``` currently accepts two options:

 - ```:max-waiting``` is used to describe the maximum number of items that can hold on
   to a lock at once. This mechanism is to provide back pressure on the incoming
   stream so system resources aren't consumed until they're gone.
 - ```:clean-up-interval``` is used to describe how often Depends should scan for
   realized locks to be evicted from the dependency map. By default, this value
   is 15000 milliseconds.

Whatever stream abstraction is used for the ```output``` argument will get items
with the structure ```{:depends/data ... :depends/complete << … >> }``` The
message that came in from input is what is in ```:depends/data``` and
```:depends/complete``` is a Manifold deferred which, when realized will release
its lock on any dependencies that message may have had. So with that said,
Depends can't just figure out what your dependencies are. That would be crazy!
You need to specify that for every message that comes into the ```input```
a unique identifier that indicates what it's doing. This is important because even
though something isn't ready to run yet, it doesn't mean that something that
arrived after it, can't go ahead of any tasks that are waiting. There are
```:read``` and ```:write``` dependencies. A ```:read``` on the same identifier
will not block subsequent ```:read``` dependencies with the same identifier.
```:write``` however, must wait for all ```:read``` events that came before it
to complete and blocks all subsequent ```:read``` and ```:write``` events that
come after a ```:write```.

It's very easy to signal that the task is complete. You simply need to realize
the ```:depends/complete``` deferred value. That's it.

```clj
(def input (s/stream 10))
(def output (s/stream 10))
(def system (depends/dependify input output))

(s/put!
  input
  ^{:dependencies {[{:anything "you want"} 123] :write}}
  ["Only objects that implement the IMetas protocol can be used."])
;;; << true >> 

(s/put!
  input
  ^{:dependencies {[{:anything "you want"} 123] :read}}
  {:more "data in" :even-more "places"})
;;; << true >> 
   
(s/put! input {:data "with no dependencies"})
;;; << true >>

(def i1 (s/take! output))
i1
;;; << {:depends/data ["Only objects that implement the IMetas protocol can be used."], :depends/complete << … >>} >>
   
(def i2 (s/take! output))
i2
;;; << {:depends/data {:data "with no dependencies"}, :depends/complete << … >>} >>

(def i3 (s/take! output))
i3
;;; << … >>
   
(d/success! (:depends/complete @i1) true)
;;; true

i1
;;; << {:depends/data ["Only objects that implement the IMetas protocol can be used."], :depends/complete << true >>} >>

i3
;;; << {:depends/data {:more "data in", :even-more "places"}, :depends/complete << … >>} >>
```

As you can see, once the deferred on the first item was completed, the second
item showed up but, not until the third message, which had no dependencies, was
processed before the first was "completed".

What if something goes wrong? Will these locks last forever and deadlock
everything that comes after it? Out of the box, yes, that's what will happen
however, it's not hard to make a simple fix for this. Manifold already provides
a timeout mechanism for deferred values and Depends has a stream mapping
function that will automatically apply a timeout for every item in a stream
coming its way. So you could apply this by doing the following:

```clj
(def input (s/stream 10))
(def output (s/stream 10))
(def pre-output (s/stream))
(s/connect (depends/map-timeout pre-output 1000) output)
(def system (depends/dependify input pre-output))
```

Now every message that gets put on the output buffered stream will have no less
than 1 second to complete before its lock it automatically dropped.

TODO: Finish the README.

## License

### Copyright and credits
 - VLACS© <jdoane@vlacs.org> 2016
 - Jon Doane <jrdoane@gmail.com> 2016

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
