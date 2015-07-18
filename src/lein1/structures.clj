(ns lein1.structures)

(comment
  {client [id connection action-ids vehicle-id
           {:id 0
            :connection "<Connection>"
            :signals #{-30 1 4 10 127}
            :vehicle-id 3}]


   body [id position-x position-y angle body old-state
         {:id 0
          :position-x 10.5
          :position-y 3.4
          :angle 2.3
          :b2d-body "<Body>"
          :old-state {:id 0
                      :position-x 9.5
                      :position-y 3.1
                      :angle 2.2
                      :b2d-body "<Body>"}}]

   body-blueprint [id shape-id material-id texture-id body-type-id
                   relative-position-x relative-position-y relative-angle
                   {:id 2
                    :shape-id 3
                    :material-id 10
                    :texture-id 10
                    :b2d-body-type-id 0
                    :relative-position-x 1
                    :relative-position-y 3
                    :relative-angle 0}]

   vehicle [id actions variables signals
            body-local->global body-global->local
            joint-local->global joint-global->local
            {:id 1
             :actions [{:last-exec 123123
                        :cooldown 1000
                        :condition (fn [])
                        :function (fn [variables bodies joints sensors]
                                    (println 0))}
                       {:last-exec 122332
                        :cooldown 0
                        :function (fn [variables bodies joints sensors]
                                    (println 1))}]
             :signals #{}
             :variables {:apple 10 :banana 30}
             :body-local->global {0 0 1 1 2 2}
             :body-global->local {0 0 1 1 2 2}
             :joint-local->global {0 0 1 1 2 2}
             :joint-global->local {0 0 1 1 2 2}}]

   vehicle-blueprint [id body-blueprints joint-blueprints sensor-blueprints actions]

   action [cooldown last-exec condition function]

   anchor [position-x position-y angle]

   joint [local-body-id1 local-body-id2 special]

   joint-blueprint [local-body-id1 local-body-id2 special]

   material [density restitution friction]})

; vehicle function conditions:
;  local variables (user made data structures)
;  global variables (current time, etc)
;  bodies
;  joints
;  sensors


