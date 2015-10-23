(ns timezonner.routes.services
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [timezonner.db.core :as db]
            [timezonner.middleware :refer [auth-backend wrap-auth get-token]]
            [buddy.hashers :as hashers]
            [clojure.string :as st]))

(defn escape-html
  "Change special characters into HTML character entities."
  [text]
  (.. ^String text
    (replace "&"  "&amp;")
    (replace "<"  "&lt;")
    (replace ">"  "&gt;")
    (replace "\"" "&quot;")
    (replace "'" "&#39;")))

(defn filter-sec [entity]
  (into {} (map 
             #(let [[k v] %1] 
                (if (string? v) 
                  [k (escape-html v)] 
                  [k v])) 
             entity)))

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
(s/defschema UserRegister {:name String
                      :email String
                      :pass (s/maybe String)})

(defapi service-routes
  (ring.swagger.ui/swagger-ui
   "/swagger-ui")  
  (swagger-docs
    {:info {:title "API for users and timezones"}}) ;JSON docs available at the /swagger.json route
  (context* "/api" []
    (context* "/timezones" []
            :tags ["Timezones"]
            (GET* "/" {:as request}
                  :return       (s/maybe [Timezone])
                  :summary     "Retrieves a list of timezones, restricted per user"
                  :middlewares [wrap-auth]
                  :header-params [authorization :- String]
                  (let [logged-id (get-in request [:identity :id])
                        logged-admin? (< 0 (get-in request [:identity :isadmin]))
                        zones (if logged-admin? 
                                (db/get-timezones) 
                                (db/get-timezones-by-user {:addedby logged-id}))]
                    (if zones
                      (ok (map  #(select-keys % (keys Timezone)) zones))
                      (ok '()))))

            (POST* "/" {:as request}
                   :return   (s/maybe Timezone)
                   :middlewares [wrap-auth]
                   :header-params [authorization :- String]
                   :body     [tzone (s/maybe TimezoneCreate)]
                   :summary  "Creates a timezone from a submited JSON"                         
                   (let [logged-id (get-in request [:identity :id])]
                     (if-let [res (db/create-timezone<! 
                          (merge (select-keys (filter-sec tzone) (keys Timezone)) 
                                {:addedby logged-id}))]
                       (created (assoc (filter-sec tzone) :id (val (first res))))
                       (bad-request))))
            
            (GET* "/:id" {:as request}
                  :return       (s/maybe Timezone)
                  :middlewares [wrap-auth]
                  :header-params [authorization :- String]
                  :path-params [id :- Long]
                  :summary     "Retrieves a specific timezone, by ID"
                  (let [full-zone (first (db/get-timezone {:id id}))
                        tzone (select-keys full-zone (keys Timezone))
                        logged-id (get-in request [:identity :id])                        
                        logged-admin? (< 0 (get-in request [:identity :isadmin]))]
                    (if (and (not logged-admin?) (or (nil? (:id tzone)) 
                            (not= logged-id (:addedby full-zone)))) 
                      (not-found "No such timezone") 
                      (ok tzone))))  

            (PUT* "/:id" {:as request}
                  :return   (s/maybe Timezone)
                  :middlewares [wrap-auth]
                  :header-params [authorization :- String]                      
                  :path-params [id :- Long]
                  :body     [tzone (s/maybe TimezoneCreate)]
                  :summary  "Updates a specified timezone from a submited JSON"
                  (let [full-zone (first (db/get-timezone {:id id}))
                        logged-id (get-in request [:identity :id])                        
                        logged-admin? (< 0 (get-in request [:identity :isadmin]))]
                    (if (and (not logged-admin?) (or (nil? (:id full-zone)) 
                            (not= logged-id (:addedby full-zone))))
                      (not-found "No such timezone")
                      (if (db/update-timezone! 
                              (merge (select-keys (filter-sec tzone) (keys TimezoneCreate)) {:id id}))
                         (ok (merge (filter-sec tzone) {:id id}))
                         (bad-request)))))
            
            (DELETE* "/:id" {:as request}
                  :return String
                  :middlewares [wrap-auth]
                  :header-params [authorization :- String]                     
                  :path-params [id :- Long]
                  :summary  "Deletes a specified timezone with ID"
                  (let [full-zone (first (db/get-timezone {:id id}))
                        tzone (select-keys full-zone (keys Timezone))
                        logged-id (get-in request [:identity :id])                        
                        logged-admin? (< 0 (get-in request [:identity :isadmin]))]
                    (if (and (not logged-admin?) (or (nil? (:id tzone)) 
                            (not= logged-id (:addedby full-zone))))
                      (not-found "No such timezone")
                      (if (db/delete-timezone! {:id id})
                       (ok "Deleted")
                       (bad-request))))))

    (context* "/users" []
            :tags ["Users"]
            :summary "User related operations"
            (GET* "/" {:as request}
                  :return       (s/maybe [User])
                  :middlewares [wrap-auth]
                  :header-params [authorization :- String]
                  :summary     "Retrieves a list of users, restricted to admins"
                  (let [logged-admin? (< 0 (get-in request [:identity :isadmin]))
                        users (db/get-users)]
                    (if (and logged-admin? users)
                      (ok (map  #(merge 
                                   (select-keys % (keys User)) 
                                   {:isadmin (not (zero? (:isadmin %)))}) users))
                      (ok '()))))

            (POST* "/" {:as request}
                   :return   (s/maybe User)
                   :middlewares [wrap-auth]
                   :header-params [authorization :- String]
                   :body     [usr (s/maybe UserCreate)]
                   :summary  "Creates a user from a submited JSON"
                   (if-let [res (db/create-user<! 
                         (merge (select-keys (filter-sec usr) (keys UserCreate)) 
                                {:pass (hashers/encrypt (:pass usr))}))]
                     (created (assoc (filter-sec usr) :id (val (first res))))
                     (bad-request)))
            
            (GET* "/:id" {:as request}
                  :return       (s/maybe User)
                  :middlewares [wrap-auth]
                  :header-params [authorization :- String]
                  :path-params [id :- Long]
                  :summary     "Retrieves a specific user, by ID"
                  (let [usr (select-keys 
                                   (first (db/get-user {:id id})) 
                                   (keys User))
                        logged-admin? (< 0 (get-in request [:identity :isadmin]))]
                    (if (or (nil? (:id usr)) (not logged-admin?))
                      (not-found "No such user") 
                      (ok (merge usr {:isadmin (< 0 (:isadmin usr))})))))

            (PUT* "/:id" {:as request}
                  :return   (s/maybe UserCreate)
                  :middlewares [wrap-auth]
                  :header-params [authorization :- String]                      
                  :path-params [id :- Long]
                  :body     [usr (s/maybe UserCreate)]
                  :summary  "Updates a specified user from a submited JSON"
                  (let [logged-admin? (< 0 (get-in request [:identity :isadmin]))]
                    (if (or (nil? (first (db/get-user {:id id}))) 
                            (not logged-admin?))
                      (not-found)
                      (if (db/update-user! 
                              (merge (select-keys usr (keys UserCreate)) {:id id}))
                         (ok usr)
                         (bad-request)))))
            
            (DELETE* "/:id" {:as request}
                  :return String
                  :middlewares [wrap-auth]
                  :header-params [authorization :- String]                     
                  :path-params [id :- Long]
                  :summary  "Deletes a specified user with ID"
                  (let [logged-admin? (< 0 (get-in request [:identity :isadmin]))]
                    (if (and logged-admin? (db/delete-user! {:id id}))
                     (ok "Deleted")
                     (bad-request)))))
    
    (POST* "/login" []
            :tags ["Security"]
            :body-params [email :- String
                          password :- String]
            (if-let [usr (first (db/get-user-by-email {:email email}))]
              (if (hashers/check password (:pass usr))
                (ok {:token (get-token usr)})
                (not-found []))
              (not-found [])))
    
    (POST* "/register" {:as request}
                   :tags ["Security"]
                   :return   (s/maybe User)
                   :body     [usr (s/maybe UserRegister)]
                   :summary  "Registers a user from a submited JSON"
                   (if (db/create-user<! 
                         (merge (select-keys (filter-sec usr) (keys UserCreate)) 
                                {:isadmin 0 
                                 :pass (hashers/encrypt (:pass usr))}))
                     (created (filter-sec usr))
                     (bad-request)))))