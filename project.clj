(defproject lein1 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure-android/clojure "1.7.0-RC1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.esotericsoftware/kryonet "2.22.0-RC1"]
                 [overtone/at-at "1.2.0"]
                 [com.badlogicgames.gdx/gdx "1.6.1"]
                 [com.badlogicgames.gdx/gdx-backend-lwjgl "1.6.1"]
                 [com.badlogicgames.gdx/gdx-box2d "1.6.1"]
                 [com.badlogicgames.gdx/gdx-box2d-platform "1.6.1"
                  :classifier "natives-desktop"]
                 [com.badlogicgames.gdx/gdx-platform "1.6.1"
                  :classifier "natives-desktop"]]

  :source-paths ["src/lein1"]
  :profiles {:uberjar {:aot :all}})
