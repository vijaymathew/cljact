;; Copyright (c) Vijay Mathew Pandyalakal <vijay.the.lisper@gmail.com>. All rights reserved.
;; See the COPYING file for licensing terms.

(ns ^{:doc "A tiny actor library."
      :author "Vijay Mathew Pandyalakal"}
  cljact.core
  (:require [clojure.core.match :refer [match]])
  (:import [java.util.concurrent Executors ConcurrentLinkedQueue]))

(def pool (Executors/newCachedThreadPool))

(defmacro schedule
  [expr]
  `(.submit pool (fn [] ~expr)))

(defmacro defact
  [name & pat-disp]
  (let [docstr (if (string? (first pat-disp)) (first pat-disp) "")
        pat-disp (if (seq docstr) (rest pat-disp) pat-disp)
        tl (when (> (count pat-disp) 1)
             (loop [pds pat-disp, tl []]
               (if (seq pds)
                 (recur (nthrest pds 2)
                   (conj tl (first pds) `(schedule ~(second pds))))
                 tl)))
        multi? (not tl)
        hd (if (symbol? (first pat-disp)) (first pat-disp) 'identity)]
    `(def ~(symbol name) ~docstr
       (let [msg-q# (ConcurrentLinkedQueue.)
             ~(symbol 'self) (atom nil)
             callback# (if ~multi?
                         (fn [m2#]
                           (~(symbol hd) m2#))
                         (fn [m3#]
                           (match m3# ~@tl)))
             handler# (fn [cont#]
                        (if-let [m# (.poll msg-q#)]
                          (do (callback# m#)
                              (when (.peek msg-q#)
                                (schedule (cont# cont#))))
                          (Thread/yield)))
             adder# (fn [msg#] (let [schd# (.peek msg-q#)]
                                 (.add msg-q# msg#)
                                 (when-not schd#
                                   (schedule (handler# handler#)))
                                 nil))]
         (reset! ~(symbol 'self) adder#)
         adder#))))

(defn shutdown
  []
  (.shutdown pool))
