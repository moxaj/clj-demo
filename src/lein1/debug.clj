(ns lein1.debug
  (:require [lein1.common :as common :refer :all]
            [lein1.util :as util :refer :all])
  (:import [com.badlogic.gdx.physics.box2d CircleShape])
  (:gen-class))

;;;; ========================================================================================
;;;; Body templates

(defn generate-body
  [id]
  (let [state {:id id :position-x (rand-int 10) :position-y (rand-int 10) :angle (rand-int 10)}]
    (assoc state :old-state state)))

(defn generate-bodies
  [n]
  (repeatedly n #(generate-body (rand-int 100000))))

(def debug-anchor {:anchor-position-x 0
                   :anchor-position-y 0
                   :anchor-angle 0})

(def debug-body-blueprint {:shape-id 0
                           :material-id 0
                           :texture-id 0
                           :relative-position-x 0
                           :relative-position-y 0
                           :relative-angle 0
                           :body-type-id 0})

(def debug-material {:density 10 :restitution 0 :friction 1})

(defn create-debug-shape
  []
  (CircleShape.))

(def debug-vehicle-blueprint
  {})
