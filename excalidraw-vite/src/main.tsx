import {createRoot} from "react-dom/client";
import "./styles.css";
import {App} from "./App";
import {BridgeContext} from "./bridge-context";
import {ExcalidrawApiBridge} from "./ExcalidrawApiBridge";
import {DebugHelperView} from "./DebugHelperView";

const rootContainer = document.getElementById("root");
const root = createRoot(rootContainer!)

const bridge = new ExcalidrawApiBridge();

root.render(
        <BridgeContext.Provider value={{bridge}}>
            <DebugHelperView/>
            <App/>
        </BridgeContext.Provider>
);