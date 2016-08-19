A tiny library for concurrent computing with actors and asynchronous messaging.

## Example Usage

```
(use '[cljact.core])

(defact a1
  "An actor that subtracts two numbers.
   Receives a message from `sender` and sends back the difference of `x` and `y`."
  [sender x y] (sender [:result (- x y)]))

(defact a2
  "Send two numbers to `a1`. Get back the result in another message and print it."
  [:sub x y] (a1 [@self x y])
  [:result x] (println x))

(a2 [:sub 100 20])
;;-> 80

(a2 [:sub 100 200])
;;-> -100
```

```
;; An actor can choose to use multi-methods instead of patterns.
(defmulti calc (fn [args] (:rator args)))
(defmethod calc :add [args] ((:sender args) (apply + (:rands args))))
(defmethod calc :sub [args] ((:sender args) (apply - (:rands args))))

(defact calculator
  "Dispatch messages to the `calc` multimethods."
  calc)

(defact prxy
  "A proxy for `calculator`."
  [:add x y] (calculator {:sender @self :rator :add :rands [x y]})
  [:sub x y] (calculator {:sender @self :rator :sub :rands [x y]})
  result (println result))

(prxy [:add 10 100])
;;-> 110

(prxy [:sub 20 30])
;;-> -10
```

```
;; Shutdown the actor subsystem. 
(shutdown)
```
