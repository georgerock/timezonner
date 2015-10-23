(ns timezonner.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [ajax.core :refer [json-request-format json-response-format ajax-request POST GET DELETE PUT]])
  (:import goog.History))



(defn discard-response-handler [redirect? redirect-uri error]
  (fn [[ok? response]]
    (if ok? 
      (do
       (session/put! :errors [])
       (when redirect? (set! (.-location js/window) redirect-uri))) 
      (session/put! :errors (conj (session/get :errors []) error)))))

(defn prop-response-handler [session-key response-key redirect-uri error]
  (fn [[ok? response]]
    (if ok? 
      (do 
       (session/put! session-key (get (js->clj response) response-key))
       (session/put! :errors [])
       (set! (.-location js/window) redirect-uri)) 
      (session/put! :errors (conj (session/get :errors []) error)))))

(defn list-response-handler [session-key redirect? redirect-uri error]
  (fn [[ok? response]]
    (if ok? 
      (do 
       (session/put! session-key (js->clj response))
       (session/put! :errors [])
       (when redirect? (set! (.-location js/window) redirect-uri))) 
      (session/put! :errors (conj (session/get :errors []) error)))))

(defn req-error-handler [error]
  (fn [{:keys [status msg]}]
    (session/put! :errors (conj (session/get :errors []) error))))

(defn POST* [uri data handler]
  (ajax-request {:uri uri 
                 :method :post
                 :body (.stringify js/JSON (clj->js data))
                 :headers {:content-type "application/json; charset=UTF-8" 
                           :accept "application/json"
                           :authorization (session/get :login-token)}
                 :response-format (json-response-format)
                 :handler handler}))

(defn GET* [uri handler]
  (ajax-request {:uri uri 
                 :method :get
                 :headers {:content-type "application/json; charset=UTF-8" 
                           :accept "application/json"
                           :authorization (session/get :login-token)}
                 :response-format (json-response-format)
                 :handler handler}))

(defn delete-action [sess-key uri error]
  (fn [evt]
    (let [id (-> evt .-target .-dataset .-id)]
      (DELETE (str uri id) 
              {:headers 
                {:content-type "application/json; charset=UTF-8" 
                 :accept "application/json"
                 :authorization (session/get :login-token)}
               :response-format (json-response-format)
               :handler #(letfn [(rempred [item]
                          (= (int id) (get item "id")))]
                          (session/put! :errors [])
                          (session/put!
                            sess-key
                            (remove rempred
                                    (session/get sess-key []))))
               :error-handler (req-error-handler error)}))))

(defn nav-link [uri title page collapsed?]
  [:li {:class (when (= page (session/get :page)) "active")}
   [:a {:href uri
        :on-click #(reset! collapsed? true)}
    title]])

(defn navbar []
  (let [collapsed? (atom true)]
    (fn []
      [:nav.navbar.navbar-inverse.navbar-fixed-top
       [:div.container
        [:div.navbar-header
         [:button.navbar-toggle
          {:class         (when-not @collapsed? "collapsed")
           :data-toggle   "collapse"
           :aria-expanded @collapsed?
           :aria-controls "navbar"
           :on-click      #(swap! collapsed? not)}
          [:span.sr-only "Toggle Navigation"]
          [:span.icon-bar]
          [:span.icon-bar]
          [:span.icon-bar]]
         [:a.navbar-brand {:href "#/"} "TimeZonner"]]
        [:div.navbar-collapse.collapse
         (when-not @collapsed? {:class "in"})
         [:ul.nav.navbar-nav
          [nav-link "#/" "Home" :home collapsed?]
          [nav-link "#/about" "About" :about collapsed?]]
         [:ul.nav.navbar-nav.navbar-right
          [:li 
           (when (session/get :login-token)
             [:a {:href "/#/" 
                  :on-click #(do 
                               (session/put! :login-token nil) 
                               (session/put! :users nil) 
                               (session/put! :tzones nil))} 
              "Logout"])]]]]])))

(defn input [type id handler]
  [:input.form-control 
   {:type type :id id
    :on-change handler}])

(defn sel [id handler]
  [:select.form-control 
   {:id id
    :on-change handler}
   [:option {:value 1} "Yes"]
   [:option {:value 0} "No"]])

(defn horz-input [label type id handler]
  [:div.form-group
   [:label.col-sm-2.control-label label]
   [:div.col-sm-10 (input type id handler)]])

(defn inline-input [label type id handler]
  [:div.form-group
    [:label label]
    (if (= type :select)
      (sel id handler)
      (input type id handler))])

(defn tz-add-action []
  (let [zone (session/get :tz-add)
        nam (:name zone)
        city (:city zone)
        offset (:offset zone)]
    (when (and nam city offset)  
      (POST* "/api/timezones" 
            (assoc zone :offset (int (:offset zone)))
            #(let [[ok? response] %1]
              (if ok? 
                (do
                  (session/put! :tzones (conj (session/get :tzones []) (js->clj response)))
                  (session/put! :errors [])) 
                (session/put! :errors (conj (session/get :errors []) "Could not add tz"))))))))

(defn tz-add-form []
  (letfn [(change-handler [tz-key]
            (fn [evt]
              (let [tz-add (session/get :tz-add {})
                    input-val (-> evt .-target .-value)]
                (session/put! :tz-add (assoc tz-add tz-key input-val)))))]  
    [:div.well [:form.form-inline
     (inline-input "Name: " :text :name-add (change-handler :name))
     (inline-input "City: " :text :city-add (change-handler :city))
     (inline-input "Offset: " :text :offset-add (change-handler :offset))
     [:button.btn.btn-default {:on-click #(tz-add-action)} "Add"]]]))

(defn usr-add-action []
  (let [usr (session/get :usr-add)
        nam (:name usr)
        email (:email usr)
        pass (:pass usr)
        isadm (:isadmin usr)]
    (when (and nam email pass isadm)  
      (POST* "/api/users" 
            (assoc usr :isadmin (not (zero? (int (:isadmin usr)))))
            #(let [[ok? response] %1]
              (if ok? 
                (do
                  (session/put! :users (conj (session/get :users []) (js->clj response)))
                  (session/put! :errors [])) 
                (session/put! :errors (conj (session/get :errors []) "Could not add user"))))))))

(defn usr-add-form []
  (letfn [(change-handler [usr-key]
            (fn [evt]
              (let [usr-add (session/get :usr-add {})
                    input-val (if (= usr-key :isadmin)
                                (let [idx (-> evt .-target .-selectedIndex)]
                                  (.-value (nth (array-seq (-> evt .-target .-options)) idx))) 
                                (-> evt .-target .-value))]
                (session/put! :usr-add (assoc usr-add usr-key input-val)))))]  
    [:div.well [:form.form-inline
     (inline-input "Email: " :text :email-add (change-handler :email))
     (inline-input "Is Admin: " :select :isadmin-add (change-handler :isadmin))
     (inline-input "Name: " :text :name-add (change-handler :name))
     (inline-input "Password: " :password :pass-add (change-handler :pass))
     [:button.btn.btn-default {:on-click #(usr-add-action)} "Add"]]]))

(defn login-check []
  (let [login (session/get :login)
        email (:email login)
        password (:password login)]
    (when (and email password)  
      (POST* "/api/login" 
            login 
            (prop-response-handler 
              :login-token 
              "token" 
              "/#/dashboard" 
              "Invalid login")))))

(defn login-form []
  (letfn [(change-handler [login-key]
            (fn [evt]
              (let [login (session/get :login {})
                    input-val (-> evt .-target .-value)]
                (session/put! :login (assoc login login-key input-val)))))]
    [:div.container
      [:div.row
        [:div.col-md-6.col-md-offset-3 
         [:h3 "Login"]
         (for [error (session/get :errors)]
           [:div.alert.alert-danger {:role "alert"} error]) 
         [:form.form-horizontal
           (horz-input "Email" :email :email (change-handler :email))
           (horz-input "Password" :password :password (change-handler :password))
           [:div.form-group
            [:div.col-sm-10.col-sm-offset-2 
             [:button.btn.btn-default {:on-click #(login-check)} "Login"]
             " or "
             [:a {:href "/#/register"} "create account"]]]]]]]))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12 [:h3 "TimeZonner is an application for Toptal"]]]])

(defn home-page []
  (if-let [token (session/get :id-token)]
    (secretary/dispatch! "/dashboard")
    (fn []
      [:div.container
       [:div.jumbotron
        [:h1 "Welcome to timezonner"]
        [:p "You can start adding your timezones"]
        [:p [:a.btn.btn-primary.btn-lg {:href "/#/login"} "Log in now »"] 
         " or " [:a {:href "/#/register"} "register"]]]])))

(defn table [sess-key title headers row-uri]
  [:div.row
   [:div.col-md-12 
    [:h3 title]
    [:table.table.table-striped.col-md-6               
     [:thead [:tr 
        (map #(let [hrow %1 k %2]
          [:th {:key (str hrow k)}
           (clojure.string/capitalize hrow)])
          (sort headers) 
          (range (count headers)))
        [:th "Actions"]]]
     [:tbody
      (map #(let [row %1]
        [:tr {:key (str (get row "id"))} 
         (for [[k col] (sort-by key row) :when (some #{k} headers)]
           [:td {:key (str k)}
            (if (or (identical? true col) (identical? false col))
              [:select.form-control {:name k :data-id (get row "id") :value (int col)}
                 [:option {:value 1} "Yes"]
                 [:option {:value 0} "No"]] 
              [:input.form-control {:type :text 
                                    :data-id (get row "id") 
                                    :name k 
                                    :value col}])])
         [:td
          [:div.btn-group
           [:button.btn.btn-info {:data-id (get row "id")} "Update"]
           [:button.btn.btn-warning 
            {:data-id (get row "id") 
             :on-click (delete-action sess-key row-uri "Could not delete row")} 
            "Delete"]]]])
        (session/get sess-key))]]]])

(defn dash-page []
  (if (session/get :login-token) 
    (fn []
      (when (empty? (session/get :tzones []))
        (GET* "/api/timezones" (list-response-handler 
                                 :tzones false nil 
                                 "Could not retrieve timezones")))      
      (when (empty? (session/get :users []))
        (GET* "/api/users" (list-response-handler 
                                 :users false nil 
                                 "Could not retrieve users")))
      [:div.container
        (for [error (session/get :errors)]
          [:div.alert.alert-danger {:role "alert"} error])
        (table 
          :tzones
          "Timezones" 
          ["offset" "name" "city"] 
          "/api/timezones/") 
        (tz-add-form)       
        (table :users "Users" 
               ["name" "email" "isadmin"]
               "/api/users/")
        (usr-add-form)])
    (fn []
      (set! (.-location js/window) "/#/login")
      [:div.container "You are being redirected to the login page"])))

(defn login-page []  
  (if (session/get :login-token)
    (fn []
      (set! (.-location js/window) "/#/dashboard")
      [:div.container "You are being redirected to the dashboard"])
    (login-form)))

(defn register-action []
  (let [register (session/get :register)
        nam (:name register)
        email (:email register)
        password (:pass register)]
    (when (and nam email password)  
      (POST* "/api/register" 
            register 
            (discard-response-handler true "/#/login" "Invalid registration")))))

(defn register-page []
  (letfn [(change-handler [reg-key]
            (fn [evt]
              (let [register (session/get :register {})
                    input-val (-> evt .-target .-value)]
                (session/put! :register (assoc register reg-key input-val)))))]
    [:div.container
     [:div.row
      [:div.col-md-6.col-md-offset-3
       [:h3 "Register"]
       [:form.form-horizontal
        (horz-input "Name" :name :name (change-handler :name))
        (horz-input "Email" :email :email (change-handler :email))
        (horz-input "Password" :password :pass (change-handler :pass))
        [:div.form-group
         [:div.col-sm-10.col-sm-offset-2
          [:button.btn.btn-default {:on-click #(register-action)} "Register"]
          " or "
          [:a {:href "/#/login"} "log in"]]]]]]]))

(def pages
  {:home #'home-page
   :about #'about-page
   :login #'login-page
   :register #'register-page
   :dash #'dash-page})

(defn page []
  [(pages (session/get :page))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

(secretary/defroute "/about" []
  (session/put! :page :about))

(secretary/defroute "/login" []
  (session/put! :page :login))

(secretary/defroute "/register" []
  (session/put! :page :register))

(secretary/defroute "/dashboard" []
  (session/put! :page :dash))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (let [h (History.)]
   (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
   (doto h
    (.setEnabled true))))

(defn mount-components []
  (reagent/render [#'navbar] (.getElementById js/document "navbar"))
  (reagent/render [#'page] (.getElementById js/document "app")))

(defn init! []
  ;(fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))
