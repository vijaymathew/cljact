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

(defmacro act
  [& pat-disp]
  (let [options (when (map? (first pat-disp)) (first pat-disp))
        msg-handler (if options (rest pat-disp) pat-disp)
        [timeout-ms timeout-handler reschedule-timer?]
        (when options (let [to (:timeout options)]
                        [(:after to) (:do to) (if (contains? to :loop?)
                                                (:loop? to)
                                                true)]))]
    `(let [msg-q# (ConcurrentLinkedQueue.)
           ~(symbol 'self) (atom nil)
           running# (atom true)
           ~(symbol 'quit) (fn [] (reset! running# false))
           curr-time-ms# (atom (when ~timeout-ms (System/currentTimeMillis)))
           handler# (fn [cont#]
                      (when @curr-time-ms#
                        (reset! curr-time-ms# (System/currentTimeMillis)))
                      (if-let [m# (.poll msg-q#)]
                        (do (~@msg-handler m#)
                            (when (and @running# (.peek msg-q#)) (schedule (cont# cont#))))
                        (Thread/yield)))
           to-handler# (when ~timeout-ms
                         (fn [cont#]
                           (when-let [old# @curr-time-ms#]
                             (if (>= (- (System/currentTimeMillis) old#) ~timeout-ms)
                               (do ~timeout-handler
                                   (when ~reschedule-timer?
                                     (reset! curr-time-ms# (System/currentTimeMillis))
                                     (when @running# (schedule (cont# cont#)))))
                               (when @running# (schedule (cont# cont#)))))))
           adder# (fn [msg#]
                    (let [schd# (.peek msg-q#)]
                      (.add msg-q# msg#)
                      (when-not schd#
                        (when @running# (schedule (handler# handler#))))
                      msg#))]
       (reset! ~(symbol 'self) adder#)
       (when to-handler#
         (schedule (to-handler# to-handler#)))
       adder#)))

(defmacro defact
  [name & defs]
  (let [docstr (if (string? (first defs))
                 (first defs)
                 "")
        defs (if (seq docstr) (rest defs) defs)]
    `(def ~(symbol name) ~docstr (act ~@defs))))

(defn shutdown
  []
  (.shutdown pool))
