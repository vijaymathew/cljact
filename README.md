A tiny Clojure library for concurrent computing with light-weight actors and asynchronous messaging.

## Example Usage

```clojure
(require
  '[cljact.core :refer [defact act]]
  '[clojure.core.match :refer [match]])

(defact subtractor
  "Receive a message from `sender` and send back the difference of `x` and `y`."
  (fn [[sender x y]] (sender [(- x y)])))

(defact subtractor-client
  "Send two numbers to `a1`. Get back the result in another message and print it."
  #(match %
      [x y] (subtractor [@self x y])
      [x] (println x)))

(subtractor-client [100 20])
;;-> 80

(subtractor-client [100 200])
;;-> -100
```

```clojure
;; An actor can choose to use multi-methods instead of patterns.
(defmulti calc (fn [args] (:rator args)))
(defmethod calc :add [args] ((:sender args) (apply + (:rands args))))
(defmethod calc :sub [args] ((:sender args) (apply - (:rands args))))

(defact calculator
  "Dispatch messages to the `calc` multimethods."
  #(calc %))

(defact calculator-client
  "A proxy for `calculator`."
  #(match %
     [:add x y] (calculator {:sender @self :rator :add :rands [x y]})
     [:sub x y] (calculator {:sender @self :rator :sub :rands [x y]})
     result (println result)))

(calculator-client [:add 10 100])
;;-> 110

(calculator-client [:sub 20 30])
;;-> -10
```

```clojure
;; Actors are first-class and are created using the `act` special form. The atom `self` gets bound to the
;; current actor object.
(defact adder
  "Add two numbers and send the result back to the client actor."
  #(match % [from [x y]] (from {:result (+ x y)})))

(def adder-client
  "A client for adder. Stores the result in a local `state` variable."
  (let [state (atom nil)]
    (act
      #(match %
         [x y] (adder [@self [x y]])
         {:result x} (reset! state x)
         [:get] (println @state)))))

(adder-client [10 20])
(adder-client [:get])
;;-> 30
```

```clojure
;; An actor can trigger a timeout handler if no message is received
;; within a specific time period. Durations are specified with millisecond precision.
;; In the following program, if no message is send to the actor `t1` within 5 seconds, 
;; a textual warning is printed.
(defact t1
  "Echo the message to stdout, print an alert on timeout."
  {:timeout {:after 5000 :do (println "timeout in t1!")}}
  #(println %))

;; By default, the timeout handler is rescheduled. To prevent this, set the loop? option
;; to false.
(defact t2
  "Echo the message to stdout, print an alert on timeout."
  {:timeout {:after 5000 :do (println "timeout in t2!") :loop? false}}
  #(println %))

;; Every actor gets a function `quit` that it can call to stop itself
;; from receiving messages and timeout events.
(defact clock
  "Print current time. Send the message [:stop] to stop the clock."
  {:timeout {:after 1000 :do (println (System/currentTimeMillis))}}
  #(when (= % :stop) (quit)))

;; `(clock :stop)` will stop the clock.
```

```clojure
;; An actor can receive four special messages :sync, :async, :linker and :quit.
;;
;; :sync returns a function that invoke the message handler in a blocking call.
;;
;; :async returns a function that invoke the message handler asynchronously. A `future`
;; is returned that can later return the result of the computation.
;;
;; :quit will remove the actor from the scheduler so it stops responding to all
;; asynchronous messages.
;;
;; :linker returns a function that can be called to link and unlink other actors to
;; monitor the status of this actor.

;; The following program demonstrates :sync, :async and :quit.
(def counter
  "An actor with changing state."
  (let [count (atom 0)]
    (act
      #(case %
         :inc (swap! count inc)
         :value @count))))

(counter :inc)
(def counter-get (counter :sync))
(counter-get :value)
;; => 1
(def inc-with-result (counter :async))
(def f (inc-with-result :inc))
(.get f)
;; => 2

;; The next example shows how to link together actors to monitor each other.
;; The `monitor` actor will be notified whenever `client` raises an
;; unhandled exception or when it quits.
(defact client
  #(case %
     :a (throw (Exception. "blah!"))
     :b (quit "done")
     :name :nemo))

(defact monitor
  #(match %
     [:exit from reason data]
     (println ((from :sync) :name) "exited with reason" reason ", " data)))

(def linker (client :linker))
(linker :link monitor)
(client :a)
;; -> <error message captured and printed by monitor>
(client :b)
;; -> <reason for calling quit captured and printed by monitor>
(linker :unlink monitor)
````

```clojure
;; The internal thread pool and message queue used by an actor can be
;; customized as shown in the following program:
(def exec (java.util.concurrent.Executors/newFixedThreadPool 1))

(defact a1
  {:exec exec :queue (java.util.concurrent.ArrayBlockingQueue. 2)}
  #(println %))

(a1 :hello)
;; -> :hello
(.shutdown exec)
```

```clojure
;; When we are done, please don't forget to shutdown the actor subsystem!
(cljact.core/shutdown)
```
