(ns timezonner.db.core
  (:require
    [yesql.core :refer [defqueries]]
    [environ.core :refer [env]]
    [conman.core :as conman]))

(def conn {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "timezonner_dev.db"})

(defqueries "sql/queries.sql" {:connection conn})