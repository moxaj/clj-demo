(ns lein1.common
  (:require [lein1.util :as util :refer :all]
            [lein1.monitor :as monitor :refer :all])
  (:import [com.badlogic.gdx Gdx Input InputAdapter Input$Keys])
  (:gen-class))

;;;; ========================================================================================
;;;; Network settings

(def game-servers {:local "127.0.0.1"})
(def default-port 3002)
(def message-type-server {:bodies 0})
(def message-type-client {:connection {:connect 0
                                       :disconnect 1}
                          :vehicle {:join 2
                                    :leave 3}
                          :signals 4})

;;;; ========================================================================================
;;;; Body definition

(def body-fields
  [{:name :id,
    :size 4,
    :encode-fn int->byte-vector,
    :decode-fn byte-vector->int
    :threshold-fn (constantly true)}
   {:name :position-x,
    :size 4,
    :encode-fn float->byte-vector,
    :decode-fn byte-vector->float,
    :threshold-fn compare-floats}
   {:name :position-y,
    :size 4,
    :encode-fn float->byte-vector,
    :decode-fn byte-vector->float,
    :threshold-fn compare-floats}
   {:name :angle,
    :size 4,
    :encode-fn float->byte-vector,
    :decode-fn byte-vector->float,
    :threshold-fn compare-floats}])

;;;; ========================================================================================
;;;; Network traffic

(defn create-network-traffic
  []
  (let [innermost-map {:sent-bytes 0 :sent-packets 0}
        inner-map {:client->server innermost-map :server->client innermost-map}]
    (atom {:UDP inner-map :TCP inner-map :start-time (System/currentTimeMillis)})))

(def network-traffic (create-network-traffic))

(defn create-statistics
  [traffic]
  (for [protocol [:TCP :UDP]
        direction [:client->server :server->client]
        :let [sent-bytes (get-in traffic [protocol direction :sent-bytes])
              sent-packets (get-in traffic [protocol direction :sent-packets])
              start-time (get traffic :start-time)
              elapsed-time-seconds (float (/ (- (System/currentTimeMillis) start-time)
                                             1000))
              bytes-per-sec (str (format "%.2f" (/ sent-bytes elapsed-time-seconds))
                                 " byte/sec")
              packets-per-sec (str (format "%.2f" (/ sent-packets elapsed-time-seconds))
                                   " packets/sec")]]
    [protocol direction bytes-per-sec packets-per-sec]))

(defn send-message!
  [connection message {:keys [protocol direction]}]
  (let [sent-bytes (case protocol
                     :UDP (.sendUDP connection message)
                     :TCP (.sendTCP connection message))]
    (swap! network-traffic
           #(-> %
                (update-in [protocol direction :sent-bytes] + sent-bytes)
                (update-in [protocol direction :sent-packets] inc)))
    (monitor-item! 2 "Network" (create-statistics @network-traffic))
    nil))

;;;; ========================================================================================
;;;; REPL

;(def network-traffic (create-network-traffic))
