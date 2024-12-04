# Extending the Shell for Products/OEMs

---

## General Do's & Dont's

Do:
- &nbsp;

Don't
- **Don't** override classes provided by WMShellBaseModule, it makes it difficult to make
  simple changes to the Shell library base modules which are shared by all products
  - If possible add mechanisms to modify the base class behavior