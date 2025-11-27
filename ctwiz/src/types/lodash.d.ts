/* eslint-disable @typescript-eslint/no-explicit-any */

declare module "lodash" {
  export type DebouncedFunc<T extends (...args: any[]) => any> = T & {
    cancel(): void;
    flush(): ReturnType<T>;
    pending(): boolean;
  };

  const lodash: Record<string, (...args: any[]) => unknown>;
  export default lodash;
}
