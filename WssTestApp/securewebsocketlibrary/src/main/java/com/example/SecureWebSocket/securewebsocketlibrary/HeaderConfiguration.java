package com.example.SecureWebSocket.securewebsocketlibrary;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * Created by Deepak_G07 on 5/19/2016.
 */
public class HeaderConfiguration {

    private boolean mSecure;
    private String mUserInfo;
    private String mKey;
    private Set<String> mProtocols;
    private List<WebSocketExtension> mExtensions;
    private List<String[]> mHeaders;

    public void addProtocol(String protocol)
    {
        if (isValidProtocol(protocol) == false)
        {
            throw new IllegalArgumentException(
                    "'protocol' must be a non-empty string with characters in the range " +
                            "U+0021 to U+007E not including separator characters.");
        }

        synchronized (this)
        {
            if (mProtocols == null)
            {
                // 'LinkedHashSet' is used because the elements
                // "MUST all be unique strings" and must be
                // "ordered by preference. See RFC 6455, p18, 10.
                mProtocols = new LinkedHashSet<String>();
            }

            mProtocols.add(protocol);
        }
    }

    public Set<String> getmProtocols()
    {
        return mProtocols;
    }

    public void removeProtocol(String protocol)
    {
        if (protocol == null)
        {
            return;
        }

        synchronized (this)
        {
            if (mProtocols == null)
            {
                return;
            }

            mProtocols.remove(protocol);

            if (mProtocols.size() == 0)
            {
                mProtocols = null;
            }
        }
    }


    public void clearProtocols()
    {
        synchronized (this)
        {
            mProtocols = null;
        }
    }


    private static boolean isValidProtocol(String protocol)
    {
        if (protocol == null || protocol.length() == 0)
        {
            return false;
        }

        int len = protocol.length();

        for (int i = 0; i < len; ++i)
        {
            char ch = protocol.charAt(i);

            if (ch < 0x21 || 0x7E < ch || Token.isSeparator(ch))
            {
                return false;
            }
        }

        return true;
    }


    public boolean containsProtocol(String protocol)
    {
        synchronized (this)
        {
            if (mProtocols == null)
            {
                return false;
            }

            return mProtocols.contains(protocol);
        }
    }


    public void addExtension(WebSocketExtension extension)
    {
        if (extension == null)
        {
            return;
        }

        synchronized (this)
        {
            if (mExtensions == null)
            {
                mExtensions = new ArrayList<WebSocketExtension>();
            }

            mExtensions.add(extension);
        }
    }


    public void addExtension(String extension)
    {
        addExtension(WebSocketExtension.parse(extension));
    }

    public List<WebSocketExtension> getmExtensions()
    {
        return mExtensions;
    }


    public void removeExtension(WebSocketExtension extension)
    {
        if (extension == null)
        {
            return;
        }

        synchronized (this)
        {
            if (mExtensions == null)
            {
                return;
            }

            mExtensions.remove(extension);

            if (mExtensions.size() == 0)
            {
                mExtensions = null;
            }
        }
    }


    public void removeExtensions(String name)
    {
        if (name == null)
        {
            return;
        }

        synchronized (this)
        {
            if (mExtensions == null)
            {
                return;
            }

            List<WebSocketExtension> extensionsToRemove = new ArrayList<WebSocketExtension>();

            for (WebSocketExtension extension : mExtensions)
            {
                if (extension.getName().equals(name))
                {
                    extensionsToRemove.add(extension);
                }
            }

            for (WebSocketExtension extension : extensionsToRemove)
            {
                mExtensions.remove(extension);
            }

            if (mExtensions.size() == 0)
            {
                mExtensions = null;
            }
        }
    }


    public void clearExtensions()
    {
        synchronized (this)
        {
            mExtensions = null;
        }
    }


    public boolean containsExtension(WebSocketExtension extension)
    {
        if (extension == null)
        {
            return false;
        }

        synchronized (this)
        {
            if (mExtensions == null)
            {
                return false;
            }

            return mExtensions.contains(extension);
        }
    }


    public boolean containsExtension(String name)
    {
        if (name == null)
        {
            return false;
        }

        synchronized (this)
        {
            if (mExtensions == null)
            {
                return false;
            }

            for (WebSocketExtension extension : mExtensions)
            {
                if (extension.getName().equals(name))
                {
                    return true;
                }
            }

            return false;
        }
    }


    public void addHeader(String name, String value)
    {
        if (name == null || name.length() == 0)
        {
            return;
        }

        if (value == null)
        {
            value = "";
        }

        synchronized (this)
        {
            if (mHeaders == null)
            {
                mHeaders = new ArrayList<String[]>();
            }

            mHeaders.add(new String[] { name, value });
        }
    }

    public List<String[]> getmHeaders()
    {
        return mHeaders;
    }

    public void removeHeaders(String name)
    {
        if (name == null || name.length() == 0)
        {
            return;
        }

        synchronized (this)
        {
            if (mHeaders == null)
            {
                return;
            }

            List<String[]> headersToRemove = new ArrayList<String[]>();

            for (String[] header : mHeaders)
            {
                if (header[0].equals(name))
                {
                    headersToRemove.add(header);
                }
            }

            for (String[] header : headersToRemove)
            {
                mHeaders.remove(header);
            }

            if (mHeaders.size() == 0)
            {
                mHeaders = null;
            }
        }
    }


    public void clearHeaders()
    {
        synchronized (this)
        {
            mHeaders = null;
        }
    }


    public void setUserInfo(String userInfo)
    {
        synchronized (this)
        {
            mUserInfo = userInfo;
        }
    }


    public void setUserInfo(String id, String password)
    {
        if (id == null)
        {
            id = "";
        }

        if (password == null)
        {
            password = "";
        }

        String userInfo = String.format("%s:%s", id, password);

        setUserInfo(userInfo);
    }

    public String getmUserInfo()
    {
        return mUserInfo;
    }


    public void clearUserInfo()
    {
        synchronized (this)
        {
            mUserInfo = null;
        }
    }
    public void setKey(String key)
    {
        mKey = key;
    }

    public String getmKey()
    {
        return  mKey;
    }




}
