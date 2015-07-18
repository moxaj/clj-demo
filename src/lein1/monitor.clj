(ns lein1.monitor
  (:require [lein1.util :as util :refer :all])
  (:gen-class))

;;;; ========================================================================================
;;;; Channels

(def opened-channels (->> (range 8)
                          (map #(vector % true))
                          (into {})))

(def channels (atom opened-channels))

(defn open-channels!
  []
  (reset! channels opened-channels))

(defn monitor-item!
  [channel description item]
  (when (get @channels channel)
    (swap! channels assoc channel false)
    (case channel
      0 [description item]
      1 [description item]
      2 [description item]
      3 [description item] ;unused
      "No such channel."))
  item)

(def monitor-loop (create-loop open-channels! 500))

;;;; ========================================================================================
;;;; REPL

;(start-loop monitor-loop)
;(stop-loop monitor-loop)


