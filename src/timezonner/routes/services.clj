(ns timezonner.routes.services
  (:require [ring.util.http-response :refer [ok created]]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]))

(s/defschema Timezone {:id (s/maybe Long)
                      :name String
                      :city String
                      :offset s/Int})
(s/defschema User {:id (s/maybe Long)
                      :name String
                      :email String
                      :password (s/maybe String)
                      :is-admin (s/maybe Boolean)})

(defapi service-routes
  (ring.swagger.ui/swagger-ui
   "/swagger-ui")
  ;JSON docs available at the /swagger.json route
  (swagger-docs
    {:info {:title "API for users and timezones"}})
    (context* "/api" []              
      (context* "/timezones" []
                :tags ["Timezones"]
                (GET* "/" []
                      :return       (s/maybe [Timezone])
                      :summary     "Retrieves a list of timezones, restricted per user"
                      (ok [{:id 1 :name "Europe/Bucharest" :city "Alba Iulia" :offset 2}]))

                (POST* "/" []
                       :return   (s/maybe Timezone)
                       :body     [tzone (s/maybe Timezone)]
                       :summary  "Creates a timezone from a submited JSON"
                       (created tzone))
                
                (GET* "/:id" []
                      :return       (s/maybe Timezone)
                      :path-params [id :- Long]
                      :summary     "Retrieves a specific timezone, by ID"
                      (ok {:id 1 :name "Europe/Bucharest" :city "Alba Iulia" :offset 2}))

                (PUT* "/:id" []
                      :return   (s/maybe Timezone)                      
                      :path-params [id :- Long]
                      :body     [tzone (s/maybe Timezone)]
                      :summary  "Updates a specified timezone from a submited JSON"
                      (ok tzone)))

      (context* "/users" []
                :tags ["Users"]
                :summary "User related operations"
                (GET* "/" []
                      :return       (s/maybe [User])
                      :summary     "Retrieves a list of users, restricted to admins"
                      (ok [{:id 1 :name "Adrian"}]))

                (POST* "/" []
                       :return   (s/maybe User)
                       :body     [usr (s/maybe User)]
                       :summary  "Creates a user from a submited JSON"
                       (created usr))
                
                (GET* "/:id" []
                      :return       (s/maybe User)
                      :path-params [id :- Long]
                      :summary     "Retrieves a specific user, by ID"
                      (ok {:id 1 :name "Adrian"}))

                (PUT* "/:id" []
                      :return   (s/maybe User)
                      :path-params [id :- Long]
                      :body     [usr (s/maybe User)]
                      :summary  "Updates a specified user from a submited JSON"
                      (ok usr)))))
