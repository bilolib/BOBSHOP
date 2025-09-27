
# BOBSHOP

[![GitHub](https://img.shields.io/badge/GitHub-Source-black?logo=github)](https://github.com/bilolib/BOBSHOP)
[![License](https://img.shields.io/badge/License-MIT--NC-blue?logo=opensourceinitiative)](https://github.com/bilolib/BOBSHOP/blob/main/LICENSE.md)
[![MyProject](https://img.shields.io/badge/More_Project-BOBCASE-orange?logo=opensourceinitiative)](https://modrinth.com/plugin/bobcase)


</div>

![BobShopBanner](https://cdn.modrinth.com/data/cached_images/87efdcf3d39987d8f92a519ef34e64ea4a57804d.png)



# Required Plugins
- 💰 [Vault](https://modrinth.com/plugin/vault) (required)  
- Any Vault-compatible economy plugin (e.g. EssentialsX Economy, CMI, XConomy, iConomy, etc.)

## Notes
- External hologram plugins are no longer required.  
- BOBShop now includes its **own built-in hologram system** for item and shop displays.  



# Features
- 🛒 Create player shops with chests or barrels
- 📜 Automatic sign creation and updating (buy/sell modes, prices, owner name, stock amount)
- 💰 Fully integrated with Vault economy (supports all Vault-compatible economy plugins)
- 🪧 Customizable holograms above shops (item preview + description text)
- 🏦 Admin Shops with unlimited stock and special pricing
- ⚙️ Configurable settings (shop limits, creation cost, tax rates, currency format, etc.)
- 💾 SQLite database with automatic backups
- 🔄 Reload support for configuration and language files
- 🌍 Multi-language support via `lang` folder (e.g. English, Turkish)
- 🪙 Permission-based tax discounts (e.g. VIP players pay less tax)
- 🚫 Protection: shops cannot be destroyed or modified without proper permissions
- ✨ Built-in hologram system (no need for any external hologram plugin)

# How to Install

1. Download the latest release of **BOBShop** from the [Modrinth page](https://modrinth.com/plugin/bobshop#download).  
2. Place the `.jar` file into your server’s `plugins` folder.  
3. Make sure you have installed the required dependency:  
   - 💰 [Vault](https://modrinth.com/plugin/vault)  
   - A Vault-compatible economy plugin (e.g. EssentialsX Economy, CMI Economy, etc.)  
4. Restart your server.  
5. (Optional) Edit `config.yml` and language files in the `plugins/BOBShop` folder to customize shop limits, creation cost, taxes, messages, etc.  
6. Use `/bobshop reload` to apply config or language changes without restarting the server. 

 ### ⚠️ Important Note

**Use the latest version (v3.0+) only.**  
- This release includes a **built-in billboard hologram system** — **no external hologram plugins are needed** anymore.  
- Older versions may **require external hologram plugins** and their holograms may **not work correctly** on new servers.  
- For best stability and performance, **do not use older releases**.

# Commands & Permissions
| Command / Node        | Description                                   | Permission              |
|:---------------------:|:---------------------------------------------|:------------------------|
| `/bobshop`            | Opens the plugin settings menu                | `bobshop.admin`         |
| `/bobshop reload`     | Reloads plugin configuration and language     | `bobshop.admin`         |
| *(no command)*        | Grants **20% shop tax discount**              | `bobshop.tax.discount.20` |
| *(no command)*        | Grants **full tax exemption (0% tax)**        | `bobshop.tax.free`      |


<details>
<summary>📊 Permission-based Tax Multipliers</summary>

The `multipliers.tax_by_permission` section allows you to **adjust or override shop taxes** for players with specific permissions.

### Example (`config.yml`)
```yaml
multipliers:
  tax_by_permission:
    "bobshop.tax.discount.20": 0.8
    "bobshop.tax.free": 0.0
```
- Each key is a permission node.
- Each value is a multiplier applied to the tax percentage.

## How it works
- If a player has the permission bobshop.tax.discount.20, their tax will be multiplied by 0.8 → they pay 20% less tax.
- If a player has the permission bobshop.tax.free, their tax will be multiplied by 0.0 → they pay no tax at all. 
- Players without these permissions use the default tax values defined in config.yml.
This lets you create VIP ranks, donor perks, or staff exemptions by simply assigning the right permission.

### Example with LuckPerms
```
# Give a player 20% tax discount
/lp user Steve permission set bobshop.tax.discount.20 true

# Make a player completely tax-free
/lp user Alex permission set bobshop.tax.free true
```

</details>

Note: Currently only basic permissions are available.
More detailed and customizable permission nodes will be added in future updates.

# How to Use

## /bobshop
<table>
  <tr>
    <td>
      <img src="https://cdn.modrinth.com/data/cached_images/feb52c856eff98f691708046c1c4528041421ddc.gif" width="300px" alt="BOBShop Settings Menu GIF">
    </td>
    <td style="vertical-align: top; padding-left: 15px;">
      <h3>BOBShop Settings Menu</h3>
      <ul>
        <li><strong>Shop Creation Price</strong> – Sets the cost that players must pay to create a new market.</li>
        <li><strong>Hologram Toggle</strong> – Enable or disable holographic display for items in your shop.</li>
        <li><strong>Description Toggle</strong> – Show or hide item descriptions above the shop.</li>
        <li><strong>Shop Limit</strong> – Sets the maximum number of shops a player can create.</li>
      </ul>
    </td>
  </tr>
</table>

## If you right-click on your own market's sign

<table>
  <tr>
    <td>
      <img src="https://cdn.modrinth.com/data/cached_images/0ac75916a521c799fee5077c2d8405cc555d3dcc.gif" width="300px" alt="Market Menu GIF">
    </td>
    <td style="vertical-align: top; padding-left: 15px;">
      <h3>Market Menu</h3>
      <ul>
        <li><strong>Change Price</strong> – Adjusts the price of the item in the shop.</li>
        <li><strong>Change Item</strong> – Change the item currently for sale in the market.</li>
        <li><strong>Change Mode</strong> – Switch the market between Buy and Sell modes.</li>
        <li><strong>Description</strong> – ✨ Show a custom floating text above your market’s hologram to give players more info about your item.</li>
      </ul>
    </td>
  </tr>
</table>

## If you **Shift + Right-click** on any market's sign  

<table>
  <tr>
    <td>
      <img src="https://cdn.modrinth.com/data/cached_images/c5ff18b7fb4e5e04ab91ec1a75302e16c8838901.gif" width="300px" alt="Admin Menu GIF">
    </td>
    <td style="vertical-align: top; padding-left: 15px;">
      <h3>Admin Menu</h3>
      <ul>
        Shift + Right-click on any market to open the admin menu and manage its settings.
        <li>"<strong>Set as Admin Shop</strong> – Makes the shop's items unlimited and all incoming/outgoing money goes to the server."</li>
      </ul>
    </td>
  </tr>
</table>

## How to Sell & Buy 🛒💰

Right-click on the market's sign 🪧, then type in chat 💬 the amount you want to **buy** or **sell**.  
Make sure to check the price 💵 before confirming! ✅✨





## Note

I created this plugin inspired by **QuickShop**, adding my own ideas and features. Since I developed it alone, I may not have been able to test everything thoroughly, so some bugs might exist.

I’d really appreciate your feedback—if you encounter any issues, please let me know and I’ll do my best to fix them. Thank you for checking out **BobShop**!






## Other Plugins

### [BOBCASE](https://modrinth.com/plugin/bobcase)
🎁 **BobCase** is a simple and customizable crate plugin for Minecraft.  
- Supports different crate types  
- Fully configurable rewards and chances  
- Easy to use for both players and admins  
