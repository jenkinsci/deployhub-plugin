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
        
        if (cookie.length() == 0)
        {            
         String headerName=null;
        
         for (int i=1; (headerName = connection.getHeaderFieldKey(i))!=null; i++) {
          if (headerName.equals("Set-Cookie")) {                  
            String c = connection.getHeaderField(i); 
            cookie += c + "; ";
          }
         }  
        }    
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
    def Object doGetHttpRequestWithJson(String requestUrl){    
        return doHttpRequestWithJson("", requestUrl, "GET");    
    } 

    /**    
     * Posts the json content to the given url and ensures a 200 or 201 status on the response.    
     * If a negative status is returned, an error will be raised and the pipeline will fail.    
     */    
    def Object doPostHttpRequestWithJson(String json, String requestUrl){    
        return doHttpRequestWithJson(json, requestUrl, "POST");    
    }    

    /**    
     * Posts the json content to the given url and ensures a 200 or 201 status on the response.    
     * If a negative status is returned, an error will be raised and the pipeline will fail.    
     */    
    def Object doPutHttpRequestWithJson(String json, String requestUrl){    
        return doHttpRequestWithJson(json, requestUrl, "PUT");    
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
    
    def Object doHttpRequestWithJson(String json, String requestUrl, String verb){ 
          
        URL url = new URL(requestUrl);    
        HttpURLConnection connection = url.openConnection();    

        connection.setRequestMethod(verb);    
        connection.setRequestProperty("Content-Type", "application/json");   
        if (cookie.length() > 0)
          connection.setRequestProperty("Cookie", cookie); 
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
				
    def boolean login(String url, String userid, String pw)
    {
     this.url = url;
     this.userid = userid;
     this.pw = pw;
     
     def res = doGetHttpRequestWithJson("${url}/dmadminweb/API/login?user=" + enc(userid) + "&pass=" + enc(pw));
     
     return res.success;
    }
    
    def moveApplication(String url, String userid, String pw, String Application, String FromDomain, String Task)
    {
     if (this.url.length() == 0)
     {
      if (!login(url,userid,pw))
       return [false,"Could not login to " + url];
     }
     // Get appid
     def data = doGetHttpRequestWithJson("${url}/dmadminweb/API/application/" + enc(Application));
     def appid = data.result.id;
     
     // Get from domainid
     data = doGetHttpRequestWithJson("${url}/dmadminweb/API/domain/" + enc(FromDomain));
     def fromid = data.result.id;
     
     // Get from Tasks
     data = doGetHttpRequestWithJson("${url}/dmadminweb/GetTasks?domainid=" + fromid);
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
     data = doGetHttpRequestWithJson("${url}/dmadminweb/RunTask?f=run&tid=" + taskid + "&notes=&id=" + appid + "&pid=" + fromid);
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
    
    def deployApplication(String url, String userid, String pw, String Application, String Environment)
    {
     if (this.url.length() == 0)
     {
      if (!login(url,userid,pw))
       return [false,"Could not login to " + url];
     }

     def data = doGetHttpRequestWithJson("${url}/dmadminweb/API/deploy/" + enc(Application) + "/" + enc(Environment) + "?wait=N");
     if (data.size() == 0)
      return [false, "Could not Deploy Application '" + Application + "' to Environment '" + Environment + "'"];
     else
      return [true,data];
    }

    def getLogs(String url, String userid, String pw, String deployid)
    {
     if (this.url.length() == 0)
     {
      if (!login(url,userid,pw))
       return [false,"Could not login to " + url];
     }

					def done = 0;
					
					while (done == 0)
					{
				  def res = this.isDeploymentDone(url, userid, pw, "$deployid");
					
					 if (res[0])
					 {
						 def s = res[1];

					  if (res[1]['success'] && res[1]['iscomplete'])
							{
							 done = 1;
							}
							else
							{
							 sleep 10
							}
					 }
						else
						{
						 echo res[1];
							done = 1;
						}
					}	
					
     def data = doGetHttpRequestWithJson("${url}/dmadminweb/API/log/" + deployid);
     if (data.size() == 0)
      return [false, "Could not get log #" + deployid];
     
					def lines = data['logoutput'];
					def output = "";
					
					def i = 0;
					for (i = 0; i < lines.size(); i++) {
					  output += lines[i] + "\n";
					}

					return [true,output];
				}

    def isDeploymentDone(String url, String userid, String pw, String deployid)
    {
     if (this.url.length() == 0)
     {
      if (!login(url,userid,pw))
       return [false,"Could not login to " + url];
     }

     def data = doGetHttpRequestWithJson("${url}/dmadminweb/API/log/" + deployid + "?checkcomplete=Y");
     if (data.size() == 0)
      return [false, "Could not get log #" + deployid];

					return [true,data];
				}
								
    def approveApplication(String url, String userid, String pw, String Application)
    {
     if (this.url.length() == 0)
     {
      if (!login(url,userid,pw))
       return [false,"Could not login to " + url];
     }

     // Get appid
     def data = doGetHttpRequestWithJson("${url}/dmadminweb/API/application/" + enc(Application));
     def appid = data.result.id;
     
     // Approve appid
     data = doGetHttpRequestWithJson("${url}/dmadminweb/API/approve/" + appid);
     if (data.size() == 0)
      return [false, "Could not Approve Application '" + Application + "'"];
     else
      return [true,data];
    }   
}

