;; Copyright (c) Vijay Mathew Pandyalakal <vijay.the.lisper@gmail.com>. All rights reserved.
;; See the COPYING file for licensing terms.

(ns ^{:doc "A tiny actor library."
      :author "Vijay Mathew Pandyalakal"}
  cljact.core
  (:require [clojure.core.match :refer [match]])
  (:import [java.util.concurrent Executors ConcurrentLinkedQueue
            ExecutorService Callable]))

(def ^ExecutorService root-exec (Executors/newCachedThreadPool))

(defn schedule
  [^ExecutorService exec ^Callable f]
  (.submit exec f))

(defmacro act
  [& pat-disp]
  (let [options (when (map? (first pat-disp)) (first pat-disp))
        msg-handler (if options (rest pat-disp) pat-disp)
        [timeout-ms timeout-handler reschedule-timer?]
        (when options (let [to (:timeout options)]
                        [(:after to) (:do to)
                         (if (contains? to :loop?)
                           (:loop? to)
                           true)]))]
    `(let [^java.util.AbstractQueue
           msg-q# ~(if options
                     (or (:queue options) (ConcurrentLinkedQueue.))
                     (ConcurrentLinkedQueue.))
           exec# ~(if options (or (:exec options) 'root-exec) 'root-exec)
           links# (atom [])
           ~(symbol 'self) (atom nil)
           notify-links# (fn [reason# ex#]
                           (doseq [l# @links#]
                             (l# [:exit @~(symbol 'self) reason# ex#])))
           running# (atom true)
           ~(symbol 'quit) (fn [& args#] (reset! running# false) (notify-links# :quit args#))
           curr-time-ms# (atom (when ~timeout-ms (System/currentTimeMillis)))
           handler# (fn [cont#]
                      (when @curr-time-ms#
                        (reset! curr-time-ms# (System/currentTimeMillis)))
                      (if-let [m# (.poll msg-q#)]
                        (do (try
                              (~@msg-handler m#)
                              (catch Exception ex#
                                (notify-links# :error ex#)))
                            (when (and @running# (.peek msg-q#)) (schedule exec# #(cont# cont#))))
                          (Thread/yield)))
           to-handler# (when ~timeout-ms
                         (fn [cont#]
                           (when-let [old# @curr-time-ms#]
                             (if (>= (- (System/currentTimeMillis) old#) ~timeout-ms)
                               (do ~timeout-handler
                                   (when ~reschedule-timer?
                                     (reset! curr-time-ms# (System/currentTimeMillis))
                                     (when @running# (schedule exec# #(cont# cont#)))))
                               (when @running# (schedule exec# #(cont# cont#)))))))
           act-with-future# (fn [msg#]
                              (when @running# (schedule exec# #(~@msg-handler msg#))))
           act-blocking# (fn [msg#]
                           (~@msg-handler msg#))
           linker# (fn [action# actor#]
                     (let [r# (case action#
                                :link (swap! links# conj actor#)
                                :unlink (swap! links# (fn [x#] (remove #{actor#} x#))))]
                       (if r# true false)))
           actor# (fn [msg#]
                    (case msg#
                      :async act-with-future#
                      :sync act-blocking#
                      :linker linker#
                      :quit (~(symbol 'quit))
                      (let [schd# (.peek msg-q#)]
                        (.add msg-q# msg#)
                        (when-not schd#
                          (when @running# (schedule exec# #(handler# handler#))))
                        msg#)))]
       (reset! ~(symbol 'self) actor#)
       (when to-handler#
         (schedule exec# #(to-handler# to-handler#)))
       actor#)))

(defmacro defact
  [name & defs]
  (let [docstr (if (string? (first defs))
                 (first defs)
                 "")
        defs (if (seq docstr) (rest defs) defs)]
    `(def ~(symbol name) ~docstr (act ~@defs))))

(defn shutdown
  []
  (.shutdown root-exec))
