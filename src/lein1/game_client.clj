(ns lein1.game-client
  (:require [lein1.common :as common :refer :all]
            [lein1.util :as util :refer :all]
            [lein1.monitor :as monitor :refer :all])
  (:import [com.esotericsoftware.kryonet Server Client Listener]
           [com.badlogic.gdx.backends.lwjgl LwjglApplication LwjglApplicationConfiguration]
           [com.badlogic.gdx ApplicationAdapter]
           [com.badlogic.gdx Gdx Input InputAdapter Input$Keys])
  (:gen-class))

;;;; ========================================================================================
;;;; Top level atoms

(def game-atom (atom {:keycodes #{}
                      :bodies {}}))

(def message-queue (atom []))

;;;; ========================================================================================
;;;; Serialization

(defn deserialize-body
  [body-bytes delta]
  (let [present-fields (remove nil? (map #(if %1 %2)
                                         delta
                                         body-fields))
        field-bytes (split-at-multiple body-bytes (map :size present-fields))]
    (into {} (map #(vector (:name %1)
                           ((:decode-fn %1) %2))
                  present-fields
                  field-bytes))))

(defn calc-num-body-bytes
  [delta]
  (->> [delta body-fields]
       (apply map #(if %1 (:size %2)))
       (remove nil?)
       (apply +)))

(defn deserialize-bodies
  [message]
  (let [num-bodies (byte-vector->int (take 4 message))
        num-delta-bytes (-> body-fields
                            count
                            (* num-bodies)
                            (/ 8)
                            (Math/ceil)
                            int)
        [delta-bytes-all body-bytes-all] (split-at num-delta-bytes (drop 4 message))]
    (loop [deltas (->> delta-bytes-all
                       (mapcat byte->boolean-vector)
                       (partition (dec (count body-fields)))
                       (map (partial cons true)))
           body-bytes body-bytes-all
           bodies []
           num-created-bodies 0]
      (if (= num-created-bodies
             num-bodies)
        bodies
        (let [[delta & deltas-rest] deltas
              [body-bytes-single body-bytes-rest] (split-at (calc-num-body-bytes delta)
                                                            body-bytes)]
          (recur deltas-rest
                 body-bytes-rest
                 (conj bodies (deserialize-body body-bytes-single delta))
                 (inc num-created-bodies)))))))

;;;; ========================================================================================
;;;; Bodies

(defn update-bodies
  [game new-bodies]
  (let [indexed-bodies (->> new-bodies
                            (map (juxt :id identity))
                            (into {}))]
    (update game :bodies (fn [old-bodies]
                           (let [to-keep (into {}
                                               (filter #(contains? new-bodies (first %))
                                                       old-bodies))]
                             (merge-with merge to-keep indexed-bodies))))))

;;;; ========================================================================================
;;;; Networking

(def client-id (int 0))
(def kryo-client (Client.))

(defn store-message!
  [connection message]
  (swap! message-queue conj [connection message]))

(defn process-message
  [game message]
  (if (instance? (Class/forName "[B") message)
    (let [[message-type & message-body] (into [] message)]
      (condp = message-type
        (-> message-type-server :bodies)
        (update-bodies game (deserialize-bodies message-body))

        nil))))


(defn process-messages!
  []
  (swap! game-atom #(run! (partial process-message %)
                          @message-queue))
  (swap! message-queue empty))


(defn start-kryo-client
  [client]
  (doto (.getKryo client)
    (.register (Class/forName "[B")))
  (doto client
    (.addListener (proxy [Listener] []
                    (received [connection object]
                              (store-message! object))))
    (.start)
    (.connect 5000 (:local game-servers) default-port /default-port)))

(defn stop-kryo-client
  [client]
  (.stop client))

(defn connect-client!
  []
  (let [message (into-array Byte/TYPE (concat [(-> message-type-client :connection :connect)]
                                              (int->byte-vector client-id)))]
    (send-message! kryo-client message
                   {:protocol :TCP :direction :client->server})))

;;;; ========================================================================================
;;;; Input

(def keycodes-atom (atom #{}))
(def keycode->signal {Input$Keys/A 0
                      Input$Keys/B 1
                      Input$Keys/C 2
                      Input$Keys/D 3})

(defn on-key-down!
  [keycode]
  (swap! keycodes-atom conj keycode)
  true)

(defn on-key-up!
  [keycode]
  (swap! keycodes-atom disj keycode)
  true)

(defn send-signals!
  []
  (let [message (into-array Byte/TYPE
                            (concat [(-> message-type-client :signals)]
                                    (int->byte-vector client-id)
                                    (remove nil? (map keycode->signal @keycodes-atom))))]
    (send-message! kryo-client
                   message
                   {:protocol :UDP :direction :client->server})))

(def input-loop (create-loop send-signals! 20))

;;;; ========================================================================================
;;;; Application callbacks

(defn on-create!
  []
  (.setInputProcessor Gdx/input
                      (proxy [InputAdapter] []
                        (keyDown [keycode] (on-key-down! keycode))
                        (keyUp [keycode] (on-key-up! keycode)))))

(defn on-render!
  []
  (monitor-item! 1 "Client" [@keycodes-atom @bodies-atom]))

(defn on-dispose!
  []
  ())

;;;; ========================================================================================
;;;; Create config and game

(defn create-config
  []
  (set-all! (LwjglApplicationConfiguration.)
            fullscreen false
            title "My first clojure game!"
            vSyncEnabled true
            x -1
            y -1
            width 1000
            height 600
            resizable false))

(defn create-game
  []
  (proxy [ApplicationAdapter] []
    (create [] (on-create!))
    (render [] (on-render!))
    (resize [w h] ())
    (dispose [] (on-dispose!))))

;;;; ========================================================================================
;;;;  REPL

;(start-kryo-client!)
;(stop-kryo-client!)

;(connect-client!)
;(disconnect-client!)

;(def app (LwjglApplication. (create-game) (create-config)))

;(start-loop input-loop)
;(stop-loop input-loop)


