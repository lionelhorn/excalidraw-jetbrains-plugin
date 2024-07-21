import React from "react";
import {createRoot} from "react-dom/client";

const rootContainer = document.getElementById("root");
const root = createRoot(rootContainer!)

const IframeDev = () => {
    const iframeRef = (iframe: any) => {

        // Proxy message to frame loaded by cef to iframe
        window.addEventListener("message", (e) => {
            console.log("win=>frame", e);
            const {type, data} = e
            iframe.contentWindow?.postMessage(data, "*");
        })
    };

    return <iframe ref={iframeRef} id="dev" src={"http://localhost:5173"} width="100%" height="100%"/>;
}

root.render(<IframeDev/>);