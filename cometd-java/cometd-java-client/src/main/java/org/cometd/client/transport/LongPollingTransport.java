package org.cometd.client.transport;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.cometd.bayeux.Message;
import org.cometd.client.BayeuxClient;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * @version $Revision$ $Date$
 */
public class LongPollingTransport extends ClientTransport
{
    private final HttpClient _httpClient;

    public LongPollingTransport(Map<String,Object> options)
    {
        super("long-polling",options);
        _httpClient = new HttpClient();
    }
    
    public LongPollingTransport(Map<String,Object> options, HttpClient httpClient)
    {
        super("long-polling",options);
        _httpClient = httpClient;
    }

    public boolean accept(String bayeuxVersion)
    {
        return true;
    }

    @Override
    public void init(BayeuxClient bayeux, HttpURI uri)
    {
        super.init(bayeux, uri);
    }

    @Override
    public void send(final TransportListener listener, Message... messages)
    {
        HttpExchange httpExchange = new TransportExchange(listener);
        httpExchange.setMethod("POST");
        
        // TODO: handle extra path for handshake, connect and disconnect
        if (messages.length==1 && messages[0].isMeta())
            httpExchange.setURL(_uri+messages[0].getChannel());
        else
            httpExchange.setURL(_uri.toString());

        String content = JSON.toString(messages);
        httpExchange.setRequestContentType("application/json;charset=UTF-8");
        try
        {
            httpExchange.setRequestContent(new ByteArrayBuffer(content, "UTF-8"));
            if (_bayeux!=null) // TODO only needed for unit test
                _bayeux.customize(httpExchange);
            _httpClient.send(httpExchange);
        }
        catch (Exception x)
        {
            listener.onException(x);
        }
    }

    private class TransportExchange extends ContentExchange
    {        
        TransportListener _listener;
        
        private TransportExchange(TransportListener listener)
        {
            super(true);
            _listener=listener;
        }

        @Override
        protected void onResponseComplete() throws IOException
        {
            if (getResponseStatus() == 200)
            {
                String content=getResponseContent();
                if (content!=null && content.length()>0)
                {
                    List<Message.Mutable> messages = toMessages(getResponseContent());
                    _listener.onMessages(messages);
                }
                else
                    _listener.onProtocolError(this+" 200 null content");
            }
            else
            {
                _listener.onProtocolError(this+" "+getResponseStatus());
            }
        }

        @Override
        protected void onConnectionFailed(Throwable x)
        {
            _listener.onConnectException(x);
        }

        @Override
        protected void onException(Throwable x)
        {
            _listener.onException(x);
        }

        @Override
        protected void onExpire()
        {
            _listener.onExpire();
        }
    }

    @Override
    public void reset()
    {
        // TODO Auto-generated method stub
        
    }
}