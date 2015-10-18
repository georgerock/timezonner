(ns timezonner.routes.services
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [timezonner.db.core :as db]))

(s/defschema Timezone {:id (s/maybe Long)
                      :name String
                      :city String
                      :offset s/Int})
(s/defschema User {:id (s/maybe Long)
                      :name String
                      :email String
                      :pass (s/maybe String)
                      :isadmin (s/maybe Boolean)})
(s/defschema TimezoneCreate {:name String
                      :city String
                      :offset s/Int})
(s/defschema UserCreate {:name String
                      :email String
                      :pass (s/maybe String)
                      :isadmin (s/maybe Boolean)})

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
                      (if-let [zones (db/get-timezones)]
                        (ok (map  #(select-keys % (keys Timezone)) zones))
                        (ok '())))

                (POST* "/" []
                       :return   (s/maybe Timezone)
                       :body     [tzone (s/maybe TimezoneCreate)]
                       :summary  "Creates a timezone from a submited JSON"                         
                       (if (db/create-timezone! 
                             (merge (select-keys tzone (keys Timezone)) 
                                    {:addedby 1}))
                         (created tzone)
                         (bad-request)))
                
                (GET* "/:id" []
                      :return       (s/maybe Timezone)
                      :path-params [id :- Long]
                      :summary     "Retrieves a specific timezone, by ID"
                      (let [tzone (select-keys 
                                       (first (db/get-timezone {:id id})) 
                                       (keys Timezone))]
                        (if (nil? (:id tzone)) 
                          (not-found "No such timezone") 
                          (ok tzone))))  

                (PUT* "/:id" []
                      :return   (s/maybe TimezoneCreate)                      
                      :path-params [id :- Long]
                      :body     [tzone (s/maybe TimezoneCreate)]
                      :summary  "Updates a specified timezone from a submited JSON"
                      (if (nil? (first (db/get-timezone {:id id})))
                        (not-found)
                        (if (db/update-timezone! 
                                (merge (select-keys tzone (keys TimezoneCreate)) {:id id}))
                           (ok tzone)
                           (bad-request))))
                
                (DELETE* "/:id" []
                      :return String                     
                      :path-params [id :- Long]
                      :summary  "Deletes a specified timezone with ID"
                      (if (db/delete-timezone! {:id id})
                         (ok "Deleted")
                         (bad-request))))

      (context* "/users" []
                :tags ["Users"]
                :summary "User related operations"
                (GET* "/" []
                      :return       (s/maybe [User])
                      :summary     "Retrieves a list of users, restricted to admins"
                      (if-let [users (db/get-users)]
                        (ok (map  #(merge 
                                     (select-keys % (keys User)) 
                                     {:is_admin (not (zero? (:is_admin %)))}) users))
                        (ok '())))

                (POST* "/" []
                       :return   (s/maybe User)
                       :body     [usr (s/maybe UserCreate)]
                       :summary  "Creates a user from a submited JSON"
                       (if (db/create-user! 
                             (merge (select-keys usr (keys UserCreate)) 
                                    {:addedby 1}))
                         (created usr)
                         (bad-request)))
                
                (GET* "/:id" []
                      :return       (s/maybe User)
                      :path-params [id :- Long]
                      :summary     "Retrieves a specific user, by ID"
                      (let [usr (select-keys 
                                       (first (db/get-user {:id id})) 
                                       (keys User))]
                        (if (nil? (:id usr)) 
                          (not-found "No such user") 
                          (ok usr))))

                (PUT* "/:id" []
                      :return   (s/maybe UserCreate)                      
                      :path-params [id :- Long]
                      :body     [usr (s/maybe UserCreate)]
                      :summary  "Updates a specified user from a submited JSON"
                      (if (nil? (first (db/get-user {:id id})))
                        (not-found)
                        (if (db/update-user! 
                                (merge (select-keys usr (keys UserCreate)) {:id id}))
                           (ok usr)
                           (bad-request))))
                
                (DELETE* "/:id" []
                      :return String                     
                      :path-params [id :- Long]
                      :summary  "Deletes a specified user with ID"
                      (if (db/delete-user! {:id id})
                         (ok "Deleted")
                         (bad-request))))))
