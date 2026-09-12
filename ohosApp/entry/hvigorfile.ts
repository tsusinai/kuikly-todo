import { hapTasks } from '@ohos/hvigor-ohos-plugin';
import { kuiklyCompilePlugin, kuiklyCopyAssetsPlugin } from 'kuikly-ohos-compile-plugin';

export default {
    system: hapTasks,  /* Built-in plugin of Hvigor. It cannot be modified. */
    /* kuiklyCompilePlugin:把 shared 编成 libshared.so;kuiklyCopyAssetsPlugin:把 commonMain 资源拷进 resfile
       (鸿蒙不内置打包 assets,此前该插件只 import 未注册 → 图标全丢) */
    plugins:[kuiklyCompilePlugin(), kuiklyCopyAssetsPlugin()]  /* Custom plugin to extend the functionality of Hvigor. */
}
