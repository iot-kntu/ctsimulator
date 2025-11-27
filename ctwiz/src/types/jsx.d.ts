import type * as React from "react";

declare global {
  namespace JSX {
    type Element = React.ReactElement;
    type ElementClass = React.Component;
    type IntrinsicElements = React.JSX.IntrinsicElements;
    type IntrinsicAttributes = React.JSX.IntrinsicAttributes;
  }
}
