// vars/deployhub.groovy
class deployhub {
    String body="";    
    String message="";    
    String cookie="";   
    String url="";   
    String userid="";
    String pw=""; 
    Integer statusCode;    
    boolean failure = false;
    
 
				@NonCPS
				def _getURL(text) {
				  def matcher = text =~ '<serverURL>(.+)</serverURL>'
				  matcher ? matcher[0][1] : null
				}
				
				def getURL(env) {
					def config = "${env.JENKINS_HOME}/org.jenkinsci.plugins.deployhub.DeployHub.xml";
					return _getURL(new File(config).text);
				}
				
    def String msg() {
     return "Loading dhactions";
    }
    
    def parseResponse(HttpURLConnection connection){    
        this.statusCode = connection.responseCode;    
        this.message = connection.responseMessage;    
        this.failure = false;
        
        if(statusCode == 200 || statusCode == 201){    
            this.body = connection.content.text;//this would fail the pipeline if there was a 400    
        }else{    
            this.failure = true;    
            this.body = connection.getErrorStream().text;    
        }
        
/*        Map<String, List<String>> map = connection.getHeaderFields();
        
        if (cookie.length() == 0)
        { 
         for (Map.Entry<String, List<String>> entry : map.entrySet()) 
         {
          if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) 
          {                  
            String c = entry.getValue();
            if  (c.contains("p1=") || c.contains("p2="))
            {
              cookie = c;
            }  
          }
         }     
        } */
    }   
    
    def doGetHttpRequest(String requestUrl){    
        URL url = new URL(requestUrl);    
        HttpURLConnection connection = url.openConnection();    
       
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Cookie", cookie); 
        connection.doOutput = true;   

        //get the request    
        connection.connect();    

        //parse the response    
        parseResponse(connection);    

        if(failure){    
            error("\nGET from URL: $requestUrl\n  HTTP Status: $resp.statusCode\n  Message: $resp.message\n  Response Body: $resp.body");    
        }    

        this.printDebug("Request (GET):\n  URL: $requestUrl");    
        this.printDebug("Response:\n  HTTP Status: $resp.statusCode\n  Message: $resp.message\n  Response Body: $resp.body");    
    }  

    /**    
     * Gets the json content to the given url and ensures a 200 or 201 status on the response.    
     * If a negative status is returned, an error will be raised and the pipeline will fail.    
     */    
    def Object doGetHttpRequestWithJson(String userid, String pw, String requestUrl){    
        return doHttpRequestWithJson(userid,pw,"", requestUrl, "GET");    
    } 

    /**    
     * Posts the json content to the given url and ensures a 200 or 201 status on the response.    
     * If a negative status is returned, an error will be raised and the pipeline will fail.    
     */    
    def Object doPostHttpRequestWithJson(String userid, String pw, String json, String requestUrl){    
        return doHttpRequestWithJson(userid,pw,json, requestUrl, "POST");    
    }    

    /**    
     * Posts the json content to the given url and ensures a 200 or 201 status on the response.    
     * If a negative status is returned, an error will be raised and the pipeline will fail.    
     */    
    def Object doPutHttpRequestWithJson(String userid, String pw, String json, String requestUrl){    
        return doHttpRequestWithJson(userid,pw,json, requestUrl, "PUT");    
    }

    /**    
     * Post/Put the json content to the given url and ensures a 200 or 201 status on the response.    
     * If a negative status is returned, an error will be raised and the pipeline will fail.    
     * verb - PUT or POST    
     */    
    def String enc(String p)
    {
     return java.net.URLEncoder.encode(p, "UTF-8");
    }
    
    def Object doHttpRequestWithJson(String userid, String pw, String json, String requestUrl, String verb){ 
          
        URL url = new URL(requestUrl);    
        HttpURLConnection connection = url.openConnection();    

        connection.setRequestMethod(verb);    
        connection.setRequestProperty("Content-Type", "application/json"); 
        connection.setRequestProperty("Cookie", "p1=$userid; p2=$pw"); 
        connection.doOutput = true;    

        if (json.length() > 0)
        {
         //write the payload to the body of the request    
         def writer = new OutputStreamWriter(connection.outputStream);    
         writer.write(json);    
         writer.flush();    
         writer.close();    
        }
    
        //post the request    
        connection.connect();    

        //parse the response    
        parseResponse(connection);    

        if(failure){    
            error("\n$verb to URL: $requestUrl\n    JSON: $json\n    HTTP Status: $statusCode\n    Message: $message\n    Response Body: $body");
            return null;    
        }   
                
        return jsonParse(body);
        
  //      println("Request ($verb):\n  URL: $requestUrl\n  JSON: $json");    
  //      println("Response:\n  HTTP Status: $statusCode\n  Message: $message\n  Response Body: $body");      
    } 
    
    @NonCPS
    def jsonParse(def json) {
        new groovy.json.JsonSlurperClassic().parseText(json)
    }
    
   /**
    * Move an application version to another stage of the pipeline
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid
    * @param pw Text the DeployHub password
    * @param Application the application to move
    * @param FromDomain the domain to move from
    * @param Task the move task
    * @return Array with first element being the return code, second the msg
    **/

    def moveApplication(String url, String userid, String pw, String Application, String FromDomain, String Task)
    {
     // Get appid
     def data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/application/" + enc(Application));
     def appid = data.result.id;
     
     // Get from domainid
     data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/domain/" + enc(FromDomain));
     def fromid = data.result.id;
     
     // Get from Tasks
     data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/GetTasks?domainid=" + fromid);
     if (data.size() == 0)
      return [false,"Could not move the Application '" + Application + "' from '" + FromDomain + "' using the '" + Task + "' Task"];

      def i=0;
      def taskid = 0;
      for (i=0;i<data.size();i++)
      {
       if (data[i].name.equalsIgnoreCase(Task))
       {
        taskid = data[i].id;
        break;
       }
      }
      
     // Move App Version
     data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/RunTask?f=run&tid=" + taskid + "&notes=&id=" + appid + "&pid=" + fromid);
     if (data.size() == 0)
      return [false,"Could not move the Application '" + Application + "' from '" + FromDomain + "' using the '" + Task + "' Task"];
     else
     {
      if (data.result)
       return [true,"Moved Application '" + Application + "' from '" + FromDomain + "'"];    
      else
       return [false,"Could not move the Application '" + Application + "' from '" + FromDomain + "' using the '" + Task + "' Task"];    
     }
    }
    
  /**
    * Force a deployment to an environment
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid
    * @param pw Text the DeployHub password
    * @param Environment the application to move
    **/

    def forceDeployIfNeeded(String url, String userid, String pw, String Environment)
    {
     def data = ServersInEnvironment(url,userid,pw,Environment);
     
     def servers = data[1]['result']['servers'];

     def i = 0;
     for (i = 0; i < servers.size(); i++) 
     {
      def id = servers[i]['id'];
      data = ServerRunning(url,userid,pw,"$id");
      
      def running = data[1]['result']['data'][0][4];

      if (running.equalsIgnoreCase("false"))
         doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/mod/server/$id/?force=y");

     }
    }
    
  /**
    * List servers in an environment
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid
    * @param pw Text the DeployHub password
    * @param Environment the application to move
    * @return Array with first element being the return code, second Array of servers
    **/

    def ServersInEnvironment(String url, String userid, String pw, String Environment)
    {
     def data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/environment/" + enc(Environment));
     if (data.size() == 0)
      return [false, "Could not test server '" + server];
     else
      return [true,data];
    }
    
  /**
    * Ping the server to see if its running
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid
    * @param pw Text the DeployHub password
    * @param server the server to test
    * @return Array with first element being the return code, second details about the server
    **/

    def ServerRunning(String url, String userid, String pw, String server)
    {
     def data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/testserver/" + enc(server));
     if (data.size() == 0)
      return [false, "Could not test server '" + server];
     else
      return [true,data];
    }
    
  /**
    * Deploy an application to an environment
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid
    * @param pw Text the DeployHub password
    * @param Application Text the Application to deploy
    * @param Environment Text the target Environment
    * @return Array with first element being the return code, second the deployment id
    **/

    def deployApplication(String url, String userid, String pw, String Application, String Environment)
    {
     def data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/deploy/" + enc(Application) + "/" + enc(Environment) + "?wait=N");
     if (data.size() == 0)
      return [false, "Could not Deploy Application '" + Application + "' to Environment '" + Environment + "'"];
     else
      return [true,data];
    }

  /**
    * Get the deployment logs
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid
    * @param pw Text the DeployHub password
    * @param deployid Text the deployment id to check
    * @return Array with first element being the return code, second the log data
    **/

    def getLogs(String url, String userid, String pw, String deployid)
    {
     def done = 0;
     
     while (done == 0)
     {
      def res = this.isDeploymentDone(url, userid, pw, "$deployid");
     
      if (res != null)
      {
       if (res[0])
       {
        def s = res[1];

        if (res[1]['success'] && res[1]['iscomplete'])
         done = 1;
       }
       else
        done = 1;
      }
      
						sleep(10000); // 10 seconds
     } 
     
     def data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/log/" + deployid);

     if (data == null || data.size() == 0)
      return [false, "Could not get log #" + deployid];
     
     def lines = data['logoutput'];
     def output = "";
     
     def i = 0;
     for (i = 0; i < lines.size(); i++) {
       output += lines[i] + "\n";
     }

     return [true,output];
    }

  /**
    * Check to see if a deployment is done
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid
    * @param pw Text the DeployHub password
    * @param deployid Text the deployment id to check
    * @return Array with first element being the return code, second true/false
    **/

    def isDeploymentDone(String url, String userid, String pw, String deployid)
    {
     def data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/log/" + deployid + "?checkcomplete=Y");
     
     if (data == null)
      return [false, "Could not get log #" + deployid];
            
     if (data != null && data.size() == 0)
      return [false, "Could not get log #" + deployid];

     return [true,data];
    }

  /**
    * Approve the application version for its current pipeline stage
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid
    * @param pw Text the DeployHub password
    * @param Application Text the Application to approve
    * @return Array with first element being the return code, second approval results
    **/

    def approveApplication(String url, String userid, String pw, String Application)
    {
     // Get appid
     def data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/application/" + enc(Application));

     def appid = data.result.id;
     
     // Approve appid
     data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/approve/" + appid);
     if (data.size() == 0)
      return [false, "Could not Approve Application '" + Application + "'"];
     else
      return [true,data];
    }   

  /**
    * Update the component attrs 
    * @param url Text the url to the DeployHub server
    * @param userid Text the DeployHub userid
    * @param pw Text the DeployHub password
    * @param Component Text the Component to update
    * @param Attrs Map the key values pairs of attrs
    * @return Array with first element being the return code, second msg
    **/

    def updateComponentAttrs(String url, String userid, String pw, String Component, Map Attrs)
    {
     // Get compId
     def data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/component/" + enc(Component));

     def compid = data.result.id;
     
     def count = 0;
     def i = 0;
     def attr_str = "";

     for ( e in Attrs ) 
     {
      def key   = ${e.key};
      def value = ${e.value};

      if (count == 0)
       attr_str = attr_str + "name=" + enc(key) + "&value=" + enc(value)
      else
       attr_str = attr_str + "name" + count + "=" + enc(key) + "&value" + count + "=" + enc(value)

      count = count + 1
     }

     if (attr_str.length() > 0)
     {
      // Update Attrs for component
      data = doGetHttpRequestWithJson(userid,pw,"${url}/dmadminweb/API/setvar/component/" + compid + "?" + attr_str);
      if (data.size() == 0)
       return [false, "Could not update attributes on '" + Component + "'"];
      else
       return [true,data];
     } 
     else
      return [false, "No attributes to update on '" + Component + "'"];  
    }   
}

