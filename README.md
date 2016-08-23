A tiny Clojure library for concurrent computing with actors and asynchronous messaging.

## Example Usage

```clojure
(use '[cljact.core])
(require '[clojure.core.match :refer [match]])

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
  (fn [msg]
    (match msg
      [:add x y] (calculator {:sender @self :rator :add :rands [x y]})
      [:sub x y] (calculator {:sender @self :rator :sub :rands [x y]})
      result (println result))))

(calculator-client [:add 10 100])
;;-> 110

(calculator-client [:sub 20 30])
;;-> -10
```

```clojure
;; An actor can trigger a timeout handler if no messages are received
;; for a set duration. Durations are specified with millisecond precision.
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
;; Shutdown the actor subsystem. 
(shutdown)
```
