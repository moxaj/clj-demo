(ns lein1.game-server
  (:require [lein1.common :as common :refer :all]
            [lein1.util :as util :refer :all]
            [lein1.monitor :as monitor :refer :all])
  (:import [com.esotericsoftware.kryonet Server Client Listener]
           [com.badlogic.gdx.physics.box2d World BodyDef Body FixtureDef Fixture BodyDef$BodyType]
           [com.badlogic.gdx.math Vector2])
  (:gen-class))

;;;; ========================================================================================
;;;; Top level atoms

(def game-atom (atom {:clients {}
                      :entities {:vehicles {}
                                 :bodies {}
                                 :joints {}}}))

(def message-queue (atom []))

;;;; ========================================================================================
;;;; Networking

(def kryo-server (Server. 2000 2000))

(defn connect-client
  [game client-id connection]
  (assoc-in game [:clients client-id] {:client-id client-id
                                       :connection connection
                                       :signals #{}
                                       :vehicle-id nil}))

(defn disconnect-client
  [game client-id]
  (update game :clients dissoc client-id))

(defn update-signals
  [game client-id message-body]
  (assoc-in game [:clients client-id :signals] (into #{} message-body)))

(defn process-message
  [game [connection message]]
  (if (instance? (Class/forName "[B") message)
    (let [[[message-type] client-id-bytes message-body] (split-at-multiple (into [] message)
                                                                           [1 4 :rem])
          client-id (byte-vector->int client-id-bytes)]
      (condp = message-type
        (-> message-type-client :connection :connect)
        (connect-client game client-id connection)

        (-> message-type-client :connection :disconnect)
        (disconnect-client game client-id)

        (-> message-type-client :signals)
        (update-signals game client-id message-body)

        game))))

(defn store-message! ;; !!! called upon receiving a network message
  [connection message]
  (swap! message-queue conj [connection message]))

(defn process-messages! ;; !!! called by update-game!
  []
  (swap! game-atom #(reduce process-message % @message-queue))
  (swap! message-queue empty))

(defn start-kryo-server!
  []
  (doto (.getKryo server)
    (.register (Class/forName "[B")))
  (doto server
    (.addListener (proxy [Listener] []
                    (received [connection object]
                              (store-message! connection object))))
    (.bind default-port default-port)
    (.start)))

(defn stop-kryo-server!
  []
  (.stop server))

;;;; ========================================================================================
;;;; Bodies

(def b2d-world (World. (Vector2.) true)) ;; !!! MUTABLE

(defn get-b2d-body-type
  [b2d-body-type-id]
  (case b2d-body-type-id
    0 BodyDef$BodyType/DynamicBody
    1 BodyDef$BodyType/StaticBody
    2 BodyDef$BodyType/KinematicBody))

(defn create-b2d-body
  [b2d-world b2d-body-type-id position-x position-y angle]
  (let [b2d-body-def (set-all! (BodyDef.)
                               type (get-b2d-body-type b2d-body-type-id)
                               angle angle)]
    (.. b2d-body-def position (set position-x position-y))
    (.createBody b2d-world b2d-body-def)))

(defn get-shape
  [shape-id]
  (debug/create-debug-shape)) ;TODO

(defn get-material
  [material-id]
  debug/debug-material) ;TODO

(defn create-fixture
  [b2d-body shape material]
  (let [b2d-fixture-def (set-all! (FixtureDef.)
                                  shape shape
                                  density (:density material)
                                  friction (:friction material)
                                  restitution (:restitution material))
        b2d-fixture (.createFixture b2d-body b2d-fixture-def)]
    (.dispose shape)
    b2d-fixture))

(defn generate-id
  []
  0) ;TODO

(defn create-body
  [b2d-world bodies body-blueprint body-id anchor]
  (let [{:keys [shape-id material-id texture-id b2d-body-type-id
                relative-position-x relative-position-y relative-angle]} body-blueprint
        {:keys [anchor-position-x anchor-position-y anchor-angle]} anchor

        position-x (+ relative-position-x anchor-position-x)
        position-y (+ relative-position-y anchor-position-y)
        angle (+ relative-angle anchor-angle)
        b2d-body (create-b2d-body b2d-world b2d-body-type-id position-x position-y angle)

        shape (get-shape shape-id)
        material (get-material material-id)
        b2d-fixture (create-fixture b2d-body shape material)

        body {:id body-id :b2d-body b2d-body
              :position-x position-x :position-y position-y :angle angle}]
    (assoc bodies (:id body) (assoc body :old-state body))))

;;;; ========================================================================================
;;;; Vehicles

; create entities, create local->global and global->local maps
; create joints, create local->global and global->local maps
; update client with vehicle-id

(defn create-vehicle
  [b2d-world bodies joints vehicle-blueprint anchor]
  ())

;;;; ========================================================================================
;;;; Serialization

(defn create-partial-body
  [old-state new-state]
  (->> body-fields
       (map #(let [field-name (:name %)
                   new-value (get new-state field-name)
                   old-value (get old-state field-name)
                   threshold-fn (:threshold-fn %)]
               (if (threshold-fn old-value new-value)
                 {field-name new-value}
                 {})))
       (apply merge)))

(defn create-delta
  [partial-body]
  (map #(if (get partial-body (:name %))
          true
          false)
       (rest body-fields)))

(defn serialize-body
  [partial-body]
  (->> body-fields
       (map #(let [field-name (:name %)]
               (if-let [field-value (get partial-body field-name)]
                 ((:encode-fn %) field-value))))
       (remove nil?)
       (apply concat)))

(defn serialize-bodies
  [bodies]
  (let [partial-bodies (map #(create-partial-body (:old-state %) %)
                            bodies)
        deltas (->> partial-bodies
                    (mapcat create-delta)
                    (#(concat % (repeat (- 8 (rem (count %) 8))
                                        true)))
                    (partition 8)
                    (map boolean-vector->byte))
        message (into-array Byte/TYPE
                            (concat [(:bodies message-type-server)]
                                    (int->byte-vector (count partial-bodies))
                                    deltas
                                    (mapcat serialize-body partial-bodies)))]
    message))

;;;; ========================================================================================
;;;; Update cycle

(defn sync-body
  [body]
  (let [b2d-body (:b2d-body body)
        position (.getPosition b2d-body)]
    (assoc body
      :position-x (.x position)
      :position-y (.y position)
      :angle (.getAngle b2d-body))))

(defn sync-bodies
  [game]
  (update-in game [:entities :bodies] #(map-vals % sync-body)))

; TODO
(defn execute-vehicle-action
  [{:keys [bodies joints vehicle] :as components}
   {:keys [id cooldown last-exec function] :as action}]
  (let [current-time (System/currentTimeMillis)]
    (if (< current-time (+ last-exec cooldown))
      components
      (assoc-in (function vehicle bodies joints)
                [:vehicle :actions id]
                (assoc action last-exec current-time)))))

(defn execute-vehicle-actions
  [{:keys [vehicles bodies joints] :as entities}
   {:keys [vehicle-id actions signals variables sensors
           body-local->global body-global->local
           joint-local->global joint-global->local] :as vehicle}]

  (let [vehicle-bodies (clojure.set/rename-keys bodies body-global->local)
        vehicle-joints (clojure.set/rename-keys joints joint-global->local)

        new-variables (assoc variables :current-time (System/currentTimeMillis)) ; and some more..

        triggered-actions (filter #((:condition %) new-variables vehicle-bodies vehicle-joints sensors)
                                  actions)
        {:keys [updated-bodies
                updated-joints
                updated-vehicle]} (reduce execute-vehicle-action
                                          {:bodies vehicle-bodies
                                           :joints vehicle-joints
                                           :vehicle (assoc vehicle :variables new-variables)}
                                          triggered-actions)]

    {:vehicles (assoc vehicles vehicle-id updated-vehicle)
     :bodies (merge bodies (clojure.set/rename-keys updated-bodies body-local->global))
     :joints (merge joints (clojure.set/rename-keys updated-joints joint-local->global))}))

(defn execute-actions
  [{:keys [entities clients] :as game}]
  (assoc game :entities (reduce execute-vehicle-actions
                                entities
                                (:vehicles entities))))

(defn submit-signals-single
  [vehicle clients]
  (assoc vehicle :signals (->> clients
                               (filter #(= (:id vehicle)
                                           (:vehicle-id %)))
                               (map :signals)
                               clojure.set/union)))

(defn submit-signals
  [{:keys [clients] :as game}]
  (update-in game [:entities :vehicles]
             (fn [vehicles]
               (map-vals #(submit-signals-single % clients) vehicles))))

(defn send-bodies!
  [{:keys [entities clients] :as game}]
  (let [message (serialize-bodies (vals (:bodies entities)))]
    (run! #(send-message! (:connection %)
                          message
                          {:protocol :UDP :direction :server->client})
          (vals clients)))
  game)

(defn step-game!
  []
  (swap! game-atom #(->> %
                         submit-signals
                         execute-actions
                         sync-bodies)))

(defn update-game!
  []
  (process-messages!)
  (step-game!)
  (->> @game
       send-bodies!
       (monitor-item! 0 "Server")))

(def update-loop (create-loop update-game! 20))

;;;; ========================================================================================
;;;; REPL

;(start-kryo-server!)
;(stop-kryo-server!)

;(start-loop update-loop)
;(stop-loop update-loop)

