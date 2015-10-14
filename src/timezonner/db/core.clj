(ns timezonner.db.core
  (:require
    [yesql.core :refer [defqueries]]
    [environ.core :refer [env]]))

(def conn
  {:classname      "org.sqlite.JDBC"
   :connection-uri (:database-url env)
   :naming         {:keys   clojure.string/lower-case
                    :fields clojure.string/upper-case}})
