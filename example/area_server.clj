(ns example.area-server
  (:require
    [cljact.core :refer [defact]]
    [clojure.core.match :refer [match]]))

(defact area-server-01
  "Compute and print areas of geometric objects."
  #(match %
     [:rectangle width height] (println "Area of rectangle is" (* width height))
     [:square side] (println "Area of square is" (* side side))
     _ (println "Invalid message.")))

;; Sample usage:
;; => (area-server-01 [:rectangle 10 20])

(defact area-server-02
  "Compute and send back areas of geometric objects."
  #(match %
     [from [:rectangle width height]] (from {:area (* width height)})
     [from [:square side]] (from {:area (* side side)})
     _ (println "Invalid message.")))

(defact area-proxy
  "Send area computations to area-server-02 and print the results."
  #(match %
     [width height] (area-server-02 [area-proxy [:rectangle width height]])
     [side] (area-server-02 [area-proxy [:square side]])
     {:area area} (println "Area is" area)))

;; Usage:
;; => (area-proxy [10 20])

