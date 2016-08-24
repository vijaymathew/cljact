(ns example.chat
  (:require
    [cljact.core :refer [defact]]
    [clojure.core.match :refer [match]]))

(defn make-chat-room
  [name]
  (let [users (atom [])]
    (act
      #(match %
         [:subscribe user] (do (swap! users conj user) (user [:subscribed name]))
         [:unsubscribe user] (do (reset! users (remove #{user} @users)) (user [:unsubscribed name]))
         [:post user post] (doseq [u @users] (u [:post user post]))))))

(defn make-user
  [name]
  (act
    #(match %
       [:send room post] (room [:post name post])
       [:post user post] (println user ":" post)
       [:subscribe chat-room] (chat-room [:subscribe @self])
       [:unsubscribe chat-room] (chat-room [:unsubscribe @self])
       [:subscribed chat-room-name] (println name "subscribed to" chat-room-name)
       [:unsubscribed chat-room-name] (println name "unsubscribed from" chat-room-name))))

;; Sample usage:
(def r1 (make-chat-room "room1"))
(def a1 (make-user "user1"))
(def a2 (make-user "user2"))

(a1 [:subscribe r1])
(a1 [:send r1 "hello"])
(a2 [:subscribe r1])
(a2 [:send r1 "hi"])
(a1 [:send r1 "hi all"])
(a1 [:unsubscribe r1])
(a2 [:send r1 "ok"])
(a2 [:unsubscribe r1])
