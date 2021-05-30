(ns examples
  (:require [com.stuartsierra.component :as component]))

;;; Dummy functions to use in the examples

(defn connect-to-database [host port]
  (println ";; Opening database connection")
  (reify java.io.Closeable
    (close [_] (println ";; Closing database connection"))))

(defn execute-query [& _]
  (println ";; execute-query"))

(defn execute-insert [& _]
  (println ";; execute-insert"))

(defn new-scheduler []
  (reify component/Lifecycle
    (start [this]
      (println ";; Starting scheduler")
      this)
    (stop [this]
      (println ";; Stopping scheduler")
      this)))


;;; Example database component

;; To define a component, define a Clojure record that implements the
;; `Lifecycle` protocol.

(defrecord Database [host port connection]
  ;; Implement the Lifecycle protocol
  component/Lifecycle

  (start [component]
    (println ";; Starting database")
    ;; In the 'start' method, initialize this component
    ;; and start it running. For example, connect to a
    ;; database, create thread pools, or initialize shared
    ;; state.
    (let [conn (connect-to-database host port)]
      ;; Return an updated version of the component with
      ;; the run-time state assoc'd in.
      (assoc component :connection conn)))

  (stop [component]
    (println ";; Stopping database")
    ;; In the 'stop' method, shut down the running
    ;; component and release any external resources it has
    ;; acquired.
    (.close connection)
    ;; Return the component, optionally modified. Remember that if you
    ;; dissoc one of a record's base fields, you get a plain map.
    (assoc component :connection nil)))

;; Optionally, provide a constructor function that takes in
;; the essential configuration parameters of the component,
;; leaving the runtime state blank.

(defn new-database [host port]
  (map->Database {:host host :port port}))

;; Define the functions implementing the behavior of the
;; component to take the component itself as an argument.

(defn get-user [database username]
  (execute-query (:connection database)
    "SELECT * FROM users WHERE username = ?"
    username))

(defn add-user [database username favorite-color]
  (execute-insert (:connection database)
    "INSERT INTO users (username, favorite_color)"
    username favorite-color))


;;; Second Example Component

;; Define other components in terms of the components on which they
;; depend.

(defrecord ExampleComponent [options cache database scheduler]
  component/Lifecycle

  (start [this]
    (println ";; Starting ExampleComponent")
    ;; In the 'start' method, a component may assume that its
    ;; dependencies are available and have already been started.
    (assoc this :admin (get-user database "admin")))

  (stop [this]
    (println ";; Stopping ExampleComponent")
    ;; Likewise, in the 'stop' method, a component may assume that its
    ;; dependencies will not be stopped until AFTER it is stopped.
    this))

;; Not all the dependencies need to be supplied at construction time.
;; In general, the constructor should not depend on other components
;; being available or started.

(defn example-component [config-options]
  (map->ExampleComponent {:options config-options
                          :cache (atom {})}))


;;; Example System

;; Components are composed into systems. A system is a component which
;; knows how to start and stop other components.

;; A system can use the helper functions `start-system` and `stop-system`,
;; which take a set of keys naming components in the system to be
;; started/stopped. Order of the keys doesn't matter here.

(def example-system-components [:scheduler :app :db])

(defrecord ExampleSystem [config-options db scheduler app]
  component/Lifecycle
  (start [this]
    (component/start-system this example-system-components))
  (stop [this]
    (component/stop-system this example-system-components)))

;; When constructing the system, specify the dependency relationships
;; among components with the `using` function.

(defn example-system [config-options]
  (let [{:keys [host port]} config-options]
    (map->ExampleSystem
      {:config-options config-options
       :db (new-database host port)
       :scheduler (new-scheduler)
       :app (component/using
              (example-component config-options)
              {:database  :db
               :scheduler :scheduler})})))
;;             ^          ^
;;             |          |
;;             |          \- Keys in the ExampleSystem record
;;             |
;;             \- Keys in the ExampleComponent record

;; `using` takes a component and a map telling the system where to
;; find that component's dependencies. Keys in the map are the keys in
;; the component record itself, values are the map are the
;; corresponding keys in the system record.

;; Based on this information (stored as metadata on the component
;; records) the `start-system` function will construct a dependency
;; graph of the components, assoc in their dependencies, and start
;; them all in the correct order.

;; Optionally, if the keys in the system map are the same as in the
;; component map, they may be passed as a vector to `using`. If you
;; know all the dependencies in advance, you may even add the metadata
;; in the component's constructor:

(defrecord AnotherComponent [component-a component-b])

(defrecord AnotherSystem [component-a component-b component-c])

(defn another-component []
  (component/using
    (map->AnotherComponent {})
    [:component-a :component-b]))



;; Sample usage:
(comment

(def system (example-system {:host "dbhost.com" :port 123}))
;;=> #'examples/system

(alter-var-root #'system component/start)
;; Starting database
;; Opening database connection
;; Starting scheduler
;; Starting ExampleComponent
;; execute-query
;;=> #examples.ExampleSystem{ ... }

(alter-var-root #'system component/stop)
;; Stopping ExampleComponent
;; Stopping scheduler
;; Stopping database
;; Closing database connection
;;=> #examples.ExampleSystem{ ... }


)

;; Local Variables:
;; clojure-defun-style-default-indent: t
;; End:
